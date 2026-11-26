package com.docsmart.features.converter.domain.usecase

/** Partes XML fijas del paquete `.xlsx` (contenido, relaciones, libro y estilos). */
internal object XlsxPackage {
    const val XML_HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
    const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val CT_SHEET = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
    private const val CT_BOOK = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"
    private const val CT_STYLES = "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"

    // Estilos: 0 normal, 1 negrita; 2..6 formatos numericos; 7..11 los mismos en negrita.
    val STYLES =
        XML_HEAD + """<styleSheet xmlns="$NS_MAIN">""" +
            """<numFmts count="3"><numFmt numFmtId="164" formatCode="&quot;$&quot;#,##0"/>""" +
            """<numFmt numFmtId="165" formatCode="&quot;$&quot;#,##0.00"/>""" +
            """<numFmt numFmtId="166" formatCode="#,##0.##########"/></numFmts>""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="2"><fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill></fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="12">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
            listOf(0, 1).joinToString("") { bold ->
                listOf("3", "166", "164", "165", "9").joinToString("") { id ->
                    val font = if (bold == 1) """ applyFont="1"""" else ""
                    """<xf numFmtId="$id" fontId="$bold" fillId="0" borderId="0" xfId="0" """ +
                        """applyNumberFormat="1"$font/>"""
                }
            } +
            """</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
            """</styleSheet>"""

    fun contentTypes(count: Int): String =
        XML_HEAD + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            override("/xl/workbook.xml", CT_BOOK) +
            override("/xl/styles.xml", CT_STYLES) +
            (1..count).joinToString("") { override("/xl/worksheets/sheet$it.xml", CT_SHEET) } +
            "</Types>"

    private fun override(
        part: String,
        contentType: String,
    ): String = """<Override PartName="$part" ContentType="$contentType"/>"""

    private fun sheetTag(
        number: Int,
        name: String,
    ): String = """<sheet name="${xmlText(name)}" sheetId="$number" r:id="rId$number"/>"""

    private fun sheetRelationship(number: Int): String =
        """<Relationship Id="rId$number" Type="$NS_REL/worksheet" Target="worksheets/sheet$number.xml"/>"""

    fun packageRels(): String =
        XML_HEAD + """<Relationships xmlns="$NS_PKG_REL">""" +
            """<Relationship Id="rId1" Type="$NS_REL/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    fun workbook(sheets: List<SheetData>): String =
        XML_HEAD + """<workbook xmlns="$NS_MAIN" xmlns:r="$NS_REL"><sheets>""" +
            sheets.mapIndexed { i, s -> sheetTag(i + 1, s.name) }.joinToString("") +
            "</sheets></workbook>"

    fun workbookRels(count: Int): String =
        XML_HEAD + """<Relationships xmlns="$NS_PKG_REL">""" +
            (1..count).joinToString("") { sheetRelationship(it) } +
            """<Relationship Id="rIdStyles" Type="$NS_REL/styles" Target="styles.xml"/></Relationships>"""

    /** Escapa texto para XML y quita los caracteres de control que Excel no admite. */
    fun xmlText(text: String): String =
        text
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\uFFFE\\uFFFF]"), "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
