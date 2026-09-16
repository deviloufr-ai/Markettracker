package com.deviloufr.markettracker.data

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal read-only XLSX reader — enough to pull a BoursoBank positions export
 * into rows of strings, with no external dependency. An `.xlsx` is a ZIP of XML
 * parts; we read `xl/sharedStrings.xml` and the first worksheet with the JDK's
 * zip and DOM parsers (both present on Android). Values come back as raw strings
 * (dates as their Excel serial number), which [BoursoCsvParser.parseRows] then
 * interprets exactly like CSV cells.
 */
object XlsxReader {

    /** True when [bytes] start with the ZIP magic (`PK`), i.e. look like an XLSX/ZIP. */
    fun looksLikeXlsx(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    /** Read the first worksheet as rows of cell strings. Empty list on any failure. */
    fun readFirstSheet(bytes: ByteArray): List<List<String>> = try {
        val parts = unzip(bytes)
        val shared = parts["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val sheetName = parts.keys
            .filter { Regex("xl/worksheets/sheet\\d+\\.xml").matches(it) }
            .minOrNull()
        if (sheetName == null) emptyList() else parseSheet(parts.getValue(sheetName), shared)
    } catch (e: Exception) {
        emptyList()
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) out[entry.name] = zis.readBytes()
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return out
    }

    private fun parse(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Harden against XXE — these parts never legitimately reference external entities.
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = parse(bytes)
        val items = doc.getElementsByTagName("si")
        return (0 until items.length).map { i ->
            val si = items.item(i) as Element
            val texts = si.getElementsByTagName("t")
            buildString { for (j in 0 until texts.length) append(texts.item(j).textContent) }
        }
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val doc = parse(bytes)
        val rows = doc.getElementsByTagName("row")
        val out = ArrayList<List<String>>(rows.length)
        for (i in 0 until rows.length) {
            val cells = (rows.item(i) as Element).getElementsByTagName("c")
            val byCol = HashMap<Int, String>()
            var maxCol = -1
            for (j in 0 until cells.length) {
                val c = cells.item(j) as Element
                val ref = c.getAttribute("r")
                val col = if (ref.isNotEmpty()) columnIndex(ref) else j
                val type = c.getAttribute("t")
                val vNodes = c.getElementsByTagName("v")
                val value = when {
                    type == "s" && vNodes.length > 0 ->
                        vNodes.item(0).textContent.trim().toIntOrNull()?.let { shared.getOrElse(it) { "" } } ?: ""
                    type == "inlineStr" -> {
                        val t = c.getElementsByTagName("t")
                        if (t.length > 0) t.item(0).textContent else ""
                    }
                    vNodes.length > 0 -> vNodes.item(0).textContent
                    else -> ""
                }
                byCol[col] = value
                if (col > maxCol) maxCol = col
            }
            out.add((0..maxCol).map { byCol[it] ?: "" })
        }
        return out
    }

    /** "A" -> 0, "B" -> 1, … "AA" -> 26, from a cell ref like "AB12". */
    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break
        }
        return (n - 1).coerceAtLeast(0)
    }
}
