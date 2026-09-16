package com.deviloufr.markettracker.data

import java.text.Normalizer

/** Derives [Position]s from a flat list of [Trade]s using the average-cost method. */
object PortfolioMath {

    private const val EPS = 1e-9

    /**
     * Fold each symbol's trades (oldest first) into a single [Position]. Buys
     * raise the weighted average cost (fees included in the basis); sells book
     * realized P&L against that average and leave the average untouched. A
     * position that sells down to (about) zero is reset so a later re-buy starts
     * a fresh basis. Open positions are returned first, biggest basis on top.
     */
    fun positionsFrom(trades: List<Trade>): List<Position> =
        trades.sortedBy { it.ts }.groupBy { it.symbol }.map { (symbol, list) ->
            var qty = 0.0
            var avgCost = 0.0
            var realized = 0.0
            list.forEach { t ->
                when (t.side) {
                    TradeSide.BUY -> {
                        val newQty = qty + t.quantity
                        if (newQty > EPS) {
                            avgCost = (avgCost * qty + t.price * t.quantity + t.fees) / newQty
                        }
                        qty = newQty
                    }
                    TradeSide.SELL -> {
                        realized += (t.price - avgCost) * t.quantity - t.fees
                        qty -= t.quantity
                        if (qty <= EPS) { qty = 0.0; avgCost = 0.0 }
                    }
                }
            }
            val held = if (qty <= EPS) 0.0 else qty
            Position(symbol, held, avgCost, realized, held * avgCost)
        }.sortedWith(compareByDescending<Position> { it.isOpen }.thenByDescending { it.invested })
}

/**
 * A trade parsed from a BoursoBank CSV export, before its ISIN is resolved to a
 * Yahoo symbol. [rawSymbol] is the ISIN or mnemonic as it appeared in the file;
 * [label] is the human name for display in the import preview.
 */
data class DraftTrade(
    val rawSymbol: String,
    val side: TradeSide,
    val quantity: Double,
    val price: Double,
    val ts: Long,
    val fees: Double,
    val label: String,
    val note: String = ""
) {
    /** True when [rawSymbol] is an ISIN (needs a lookup to become a Yahoo ticker). */
    val isIsin: Boolean get() = ISIN_REGEX.matches(rawSymbol)

    companion object {
        val ISIN_REGEX = Regex("^[A-Z]{2}[A-Z0-9]{9}[0-9]$")
    }
}

/**
 * Outcome of parsing an export: the rows we understood, how many we couldn't, and
 * whether the file was a positions [snapshot] (opening positions, replaces the
 * previous snapshot on import) rather than an operations history.
 */
data class BoursoParseResult(
    val trades: List<DraftTrade>,
    val skipped: Int,
    val error: String? = null,
    val snapshot: Boolean = false
)

/**
 * Tolerant parser for BoursoBank *bourse* exports. Handles two shapes:
 *  - a **positions snapshot** (columns `name, isin, quantity, buyingPrice, …`, no
 *    buy/sell side) → one opening BUY per holding at its PRU (`buyingPrice`);
 *  - an **operations history** (a `sens`/side column) → one trade per row.
 * The exact columns vary by account and over time, so we fuzzy-match FR/EN header
 * names and parse French numbers (`1 234,56`), `dd/mm/yyyy` dates and Excel serial
 * dates. Rows we can't read are counted in [BoursoParseResult.skipped] rather than
 * failing the whole import. [parseRows] is fed by both the CSV and XLSX readers.
 */
object BoursoCsvParser {

    /** Note tag marking trades synthesised from a positions snapshot (replaced on re-import). */
    const val SNAPSHOT_NOTE = "BoursoBank (position)"

    /** Note tag for trades imported from an operations history. */
    const val HISTORY_NOTE = "BoursoBank"

    // Header synonyms, accent-stripped and lowercased. First match wins.
    private val ISIN = setOf("isin", "code isin", "codeisin")
    private val CODE = setOf("code", "symbole", "symbol", "mnemo", "mnemonique", "ticker")
    private val LABEL = setOf("libelle", "valeur", "designation", "nom", "instrument", "name", "intitule")
    private val SIDE = setOf("sens", "operation", "type operation", "nature", "sens operation")
    private val QTY = setOf("quantite", "qte", "quantity", "nombre", "nb", "nombre de titres")
    private val COST = setOf(
        "buyingprice", "buying price", "pru", "prix de revient", "prix de revient unitaire",
        "prix d achat", "prix achat", "cours d achat", "prix moyen"
    )
    private val PRICE = setOf(
        "cours", "cours execution", "cours d execution", "cours de bourse", "cours execute",
        "prix", "prix unitaire", "prix d execution", "prix execution", "price"
    )
    private val FEES = setOf("frais", "courtage", "commission", "frais de courtage", "total frais")
    private val AMOUNT = setOf("montant", "amount", "valorisation", "montant net", "montant brut", "montant total", "total")
    private val DATE = setOf(
        "date", "lastmovementdate", "last movement date", "date mouvement", "date operation",
        "date execution", "date negociation", "date de negociation", "date d operation", "dateop"
    )

    /** Parse a delimited-text (CSV) export. */
    fun parse(raw: String): BoursoParseResult {
        val lines = raw.split(Regex("\r\n|\n|\r")).filter { it.isNotBlank() }
        if (lines.isEmpty()) return BoursoParseResult(emptyList(), 0, "Fichier vide.")
        val delimiter = detectDelimiter(lines.first())
        return parseRows(lines.map { splitLine(it, delimiter) })
    }

    /**
     * Parse already-split rows (first row = header), from a CSV or an XLSX sheet.
     * Detects snapshot vs history from the presence of a cost column and the
     * absence of a side column.
     */
    fun parseRows(rows: List<List<String>>): BoursoParseResult {
        if (rows.isEmpty()) return BoursoParseResult(emptyList(), 0, "Fichier vide.")
        val header = rows.first().map { normalize(it) }

        fun col(names: Set<String>): Int =
            header.indexOfFirst { h -> h.isNotEmpty() && names.any { h == it || h.contains(it) } }

        val iIsin = col(ISIN)
        val iCode = col(CODE)
        val iLabel = col(LABEL)
        val iSide = col(SIDE)
        val iQty = col(QTY)
        val iCost = col(COST)
        val iPrice = col(PRICE)
        val iFees = col(FEES)
        val iAmount = col(AMOUNT)
        val iDate = col(DATE)

        if (iQty < 0 || (iIsin < 0 && iCode < 0 && iLabel < 0)) {
            return BoursoParseResult(
                emptyList(), 0,
                "Colonnes non reconnues. En-tête lu : ${header.filter { it.isNotEmpty() }.joinToString(" | ")}"
            )
        }

        // A cost/PRU column with no buy/sell side column means this is a holdings snapshot.
        val snapshot = iCost >= 0 && iSide < 0

        val out = ArrayList<DraftTrade>()
        var skipped = 0
        for (r in rows.drop(1)) {
            fun cell(i: Int): String = if (i in r.indices) r[i].trim() else ""

            val qty = parseNumber(cell(iQty))
            val rawSymbol = listOf(iIsin, iCode, iLabel).firstNotNullOfOrNull { idx ->
                cell(idx).takeIf { it.isNotBlank() }
            }?.uppercase()
            val label = if (iLabel >= 0) cell(iLabel) else (rawSymbol ?: "")

            val price = if (snapshot) {
                parseNumber(cell(iCost))
            } else {
                parseNumber(cell(iPrice)) ?: run {
                    // Fall back to montant / quantité when no explicit price column.
                    val amount = parseNumber(cell(iAmount))
                    if (amount != null && qty != null && qty != 0.0) kotlin.math.abs(amount / qty) else null
                }
            }

            if (qty == null || qty == 0.0 || price == null || price <= 0.0 || rawSymbol == null) { skipped++; continue }

            val side = if (snapshot) TradeSide.BUY else detectSide(cell(iSide), parseNumber(cell(iAmount)), qty)
            val ts = parseDate(cell(iDate)) ?: System.currentTimeMillis()
            val fees = parseNumber(cell(iFees))?.let { kotlin.math.abs(it) } ?: 0.0

            out.add(
                DraftTrade(
                    rawSymbol = rawSymbol,
                    side = side,
                    quantity = kotlin.math.abs(qty),
                    price = price,
                    ts = ts,
                    fees = fees,
                    label = label,
                    note = if (snapshot) SNAPSHOT_NOTE else HISTORY_NOTE
                )
            )
        }
        return BoursoParseResult(out, skipped, snapshot = snapshot)
    }

    private fun detectDelimiter(headerLine: String): Char =
        listOf(';', '\t', ',').maxByOrNull { d -> headerLine.count { it == d } } ?: ';'

    /** Minimal CSV split honoring double-quoted cells (which may contain the delimiter). */
    private fun splitLine(line: String, delimiter: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out.map { it.trim().trim('"') }
    }

    private fun detectSide(sideCell: String, amount: Double?, qty: Double?): TradeSide {
        val s = normalize(sideCell)
        return when {
            s.contains("achat") || s.contains("buy") || s == "a" -> TradeSide.BUY
            s.contains("vente") || s.contains("sell") || s == "v" -> TradeSide.SELL
            amount != null && amount > 0.0 -> TradeSide.SELL   // credit on the account = sell proceeds
            amount != null && amount < 0.0 -> TradeSide.BUY    // debit = a purchase
            qty != null && qty < 0.0 -> TradeSide.SELL
            else -> TradeSide.BUY
        }
    }

    /** Parse a French-or-English formatted number: `1 234,56`, `1,234.56`, `-42`. */
    fun parseNumber(s: String): Double? {
        if (s.isBlank()) return null
        var t = s.replace(" ", "").replace(" ", "").replace(" ", "")
            .replace("€", "").replace("%", "").trim()
        val hasComma = t.contains(',')
        val hasDot = t.contains('.')
        t = when {
            hasComma && hasDot ->
                // The rightmost of the two is the decimal separator; the other groups thousands.
                if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.')
                else t.replace(",", "")
            hasComma -> t.replace(',', '.')
            else -> t
        }
        return t.toDoubleOrNull()
    }

    /**
     * Parse `dd/mm/yyyy`, `dd-mm-yyyy`, `yyyy-mm-dd`, or an Excel serial date
     * number (as XLSX stores dates, e.g. `46000`) to epoch millis.
     */
    fun parseDate(s: String): Long? {
        val t = s.trim().substringBefore(' ')
        if (t.isBlank()) return null
        // Excel serial date: bare number in a plausible range (≈1984..2065).
        t.toDoubleOrNull()?.let { serial ->
            if (serial in 30000.0..60000.0) {
                // Excel day 25569 == 1970-01-01 (the 1900 date system, ignoring the 1900 leap bug).
                return ((serial - 25569.0) * 86_400_000.0).toLong()
            }
        }
        val parts = t.split('/', '-').mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        val (y, mo, d) = when {
            parts[0] > 31 -> Triple(parts[0], parts[1], parts[2])      // yyyy-mm-dd
            else -> Triple(parts[2], parts[1], parts[0])               // dd/mm/yyyy
        }
        return try {
            java.util.GregorianCalendar(y, mo - 1, d).timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
