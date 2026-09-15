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
    val label: String
) {
    /** True when [rawSymbol] is an ISIN (needs a lookup to become a Yahoo ticker). */
    val isIsin: Boolean get() = ISIN_REGEX.matches(rawSymbol)

    companion object {
        val ISIN_REGEX = Regex("^[A-Z]{2}[A-Z0-9]{9}[0-9]$")
    }
}

/** Outcome of parsing a CSV: the rows we understood, plus how many we couldn't. */
data class BoursoParseResult(
    val trades: List<DraftTrade>,
    val skipped: Int,
    val error: String? = null
)

/**
 * Tolerant parser for BoursoBank's *bourse* CSV exports (order/operation
 * history). The exact columns vary by account and over time, so rather than
 * hard-coding one layout we detect the delimiter and fuzzy-match French/English
 * header names, and parse French-formatted numbers (`1 234,56`) and dates
 * (`dd/mm/yyyy`). Unrecognised rows are counted in [BoursoParseResult.skipped]
 * instead of failing the whole import.
 */
object BoursoCsvParser {

    // Header synonyms, accent-stripped and lowercased. First match wins.
    private val ISIN = setOf("isin", "code isin", "codeisin")
    private val CODE = setOf("code", "symbole", "symbol", "mnemo", "mnemonique", "ticker")
    private val LABEL = setOf("libelle", "libelle valeur", "valeur", "designation", "nom", "instrument")
    private val SIDE = setOf("sens", "operation", "type operation", "nature", "type", "sens operation")
    private val QTY = setOf("quantite", "qte", "quantity", "nombre", "nb", "nombre de titres")
    private val PRICE = setOf(
        "cours", "cours execution", "cours d execution", "cours de bourse", "cours execute",
        "prix", "prix unitaire", "prix d execution", "prix execution", "price"
    )
    private val FEES = setOf("frais", "courtage", "commission", "frais de courtage", "total frais")
    private val AMOUNT = setOf("montant", "montant net", "montant brut", "montant total", "total")
    private val DATE = setOf(
        "date", "date operation", "date execution", "date negociation", "date de negociation",
        "date d operation", "dateop"
    )

    fun parse(raw: String): BoursoParseResult {
        val lines = raw.split(Regex("\r\n|\n|\r")).filter { it.isNotBlank() }
        if (lines.isEmpty()) return BoursoParseResult(emptyList(), 0, "Fichier vide.")

        val delimiter = detectDelimiter(lines.first())
        val header = splitLine(lines.first(), delimiter).map { normalize(it) }

        fun col(names: Set<String>): Int =
            header.indexOfFirst { h -> names.any { h == it || h.contains(it) } }

        val iIsin = col(ISIN)
        val iCode = col(CODE)
        val iLabel = col(LABEL)
        val iSide = col(SIDE)
        val iQty = col(QTY)
        val iPrice = col(PRICE)
        val iFees = col(FEES)
        val iAmount = col(AMOUNT)
        val iDate = col(DATE)

        if (iQty < 0 || (iIsin < 0 && iCode < 0 && iLabel < 0)) {
            return BoursoParseResult(
                emptyList(), 0,
                "Colonnes non reconnues. En-tête lu : ${header.joinToString(" | ")}"
            )
        }

        val out = ArrayList<DraftTrade>()
        var skipped = 0
        for (line in lines.drop(1)) {
            val cells = splitLine(line, delimiter)
            fun cell(i: Int): String = if (i in cells.indices) cells[i].trim() else ""

            val qty = parseNumber(cell(iQty))
            val price = parseNumber(cell(iPrice)).let {
                if (it != null && it > 0.0) it
                else {
                    // Fall back to montant / quantité when no explicit price column.
                    val amount = parseNumber(cell(iAmount))
                    if (amount != null && qty != null && qty != 0.0) kotlin.math.abs(amount / qty) else null
                }
            }
            val rawSymbol = listOf(iIsin, iCode, iLabel).firstNotNullOfOrNull { idx ->
                cell(idx).takeIf { it.isNotBlank() }
            }?.uppercase()

            if (qty == null || qty == 0.0 || price == null || rawSymbol == null) { skipped++; continue }

            val side = detectSide(cell(iSide), parseNumber(cell(iAmount)), qty)
            val ts = parseDate(cell(iDate)) ?: System.currentTimeMillis()
            val fees = parseNumber(cell(iFees))?.let { kotlin.math.abs(it) } ?: 0.0
            val label = if (iLabel >= 0) cell(iLabel) else rawSymbol

            out.add(
                DraftTrade(
                    rawSymbol = rawSymbol,
                    side = side,
                    quantity = kotlin.math.abs(qty),
                    price = price,
                    ts = ts,
                    fees = fees,
                    label = label
                )
            )
        }
        return BoursoParseResult(out, skipped)
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

    /** Parse `dd/mm/yyyy`, `dd-mm-yyyy` or `yyyy-mm-dd` to epoch millis (local midnight). */
    fun parseDate(s: String): Long? {
        val t = s.trim().substringBefore(' ')
        if (t.isBlank()) return null
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
