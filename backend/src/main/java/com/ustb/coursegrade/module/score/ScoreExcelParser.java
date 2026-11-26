package com.ustb.coursegrade.module.score;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 成绩表格解析与模板生成器：支持 .xlsx（Zip + XML，不引入额外依赖）与 .csv。
 * 输出统一为 [{row_no, student_id, usual_score, exam_score, score_level, exam_flag, usual_ratio}]。
 */
public final class ScoreExcelParser {

    private ScoreExcelParser() {
    }

    public static List<Map<String, String>> parse(InputStream in, String filename) throws Exception {
        byte[] data = in.readAllBytes();
        if (data.length == 0) {
            throw new IllegalArgumentException("文件为空");
        }
        boolean legacyXls = (filename != null && filename.toLowerCase().endsWith(".xls"))
                || (data.length >= 4
                && (data[0] & 0xFF) == 0xD0 && (data[1] & 0xFF) == 0xCF
                && (data[2] & 0xFF) == 0x11 && (data[3] & 0xFF) == 0xE0);
        if (legacyXls) {
            throw new IllegalArgumentException("暂不支持旧版 .xls 格式，请另存为 .xlsx 或 .csv 后再上传");
        }
        boolean xlsx = (filename != null && filename.toLowerCase().endsWith(".xlsx"))
                || (data.length > 1 && data[0] == 'P' && data[1] == 'K');
        List<List<String>> grid = xlsx ? readXlsx(data) : readCsv(data);
        return toRows(grid);
    }

    // ---------- xlsx ----------

    private static List<List<String>> readXlsx(byte[] data) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                zip.transferTo(bos);
                entries.put(e.getName(), bos.toByteArray());
            }
        }
        List<String> shared = new ArrayList<>();
        byte[] ss = entries.get("xl/sharedStrings.xml");
        if (ss != null) {
            Document doc = xml(ss);
            NodeList sis = doc.getElementsByTagName("si");
            for (int i = 0; i < sis.getLength(); i++) {
                shared.add(textOf((Element) sis.item(i)));
            }
        }
        String sheetName = entries.keySet().stream()
                .filter(n -> n.startsWith("xl/worksheets/sheet") && n.endsWith(".xml"))
                .sorted()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("xlsx 中没有工作表"));
        Document sheet = xml(entries.get(sheetName));
        NodeList rows = sheet.getElementsByTagName("row");
        List<List<String>> grid = new ArrayList<>();
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            NodeList cells = row.getElementsByTagName("c");
            List<String> values = new ArrayList<>();
            for (int j = 0; j < cells.getLength(); j++) {
                Element c = (Element) cells.item(j);
                int idx = colIndex(c.getAttribute("r"));
                while (values.size() <= idx) {
                    values.add("");
                }
                values.set(idx, cellValue(c, shared));
            }
            grid.add(values);
        }
        return grid;
    }

    private static String cellValue(Element c, List<String> shared) {
        String type = c.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList ts = c.getElementsByTagName("t");
            return ts.getLength() > 0 ? ts.item(0).getTextContent() : "";
        }
        NodeList vs = c.getElementsByTagName("v");
        String raw = vs.getLength() > 0 ? vs.item(0).getTextContent() : "";
        if ("s".equals(type)) {
            try {
                int idx = Integer.parseInt(raw.trim());
                return idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }
        return raw;
    }

    private static String textOf(Element si) {
        NodeList ts = si.getElementsByTagName("t");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ts.getLength(); i++) {
            sb.append(ts.item(i).getTextContent());
        }
        return sb.toString();
    }

    private static int colIndex(String ref) {
        int idx = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                idx = idx * 26 + (ch - 'A' + 1);
            } else {
                break;
            }
        }
        return Math.max(0, idx - 1);
    }

    private static Document xml(byte[] bytes) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setExpandEntityReferences(false);
        f.setNamespaceAware(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    // ---------- csv ----------

    private static List<List<String>> readCsv(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        List<List<String>> grid = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (ch == '"') {
                    if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = !quoted;
                    }
                } else if (ch == ',' && !quoted) {
                    cells.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(ch);
                }
            }
            cells.add(cur.toString());
            grid.add(cells);
        }
        return grid;
    }

    // ---------- header mapping ----------

    private static List<Map<String, String>> toRows(List<List<String>> grid) {
        int headerIdx = -1;
        for (int i = 0; i < grid.size(); i++) {
            for (String cell : grid.get(i)) {
                String h = normalize(cell);
                if (h.contains("学号") || h.equalsIgnoreCase("student_id")) {
                    headerIdx = i;
                    break;
                }
            }
            if (headerIdx >= 0) {
                break;
            }
        }
        if (headerIdx < 0) {
            throw new IllegalArgumentException("没有找到表头（需要包含“学号”列）");
        }
        Map<String, Integer> cols = new LinkedHashMap<>();
        List<String> header = grid.get(headerIdx);
        for (int i = 0; i < header.size(); i++) {
            String h = normalize(header.get(i));
            boolean isRatioCol = h.contains("占比") || h.contains("比例");
            if ((h.contains("学号") || h.equalsIgnoreCase("student_id")) && !cols.containsKey("student_id")) {
                cols.put("student_id", i);
            } else if (isRatioCol) {
                cols.put("usual_ratio", i);
            } else if (h.contains("平时")) {
                cols.put("usual_score", i);
            } else if (h.contains("等级")) {
                cols.put("score_level", i);
                if (h.contains("期末")) {
                    cols.putIfAbsent("exam_score", i);
                }
            } else if (h.contains("考试情况") || h.equals("缺考")) {
                cols.put("exam_flag", i);
            } else if ((h.contains("期末") || h.contains("考试"))
                    && !h.contains("时间") && !h.contains("地点") && !h.contains("方式")
                    && !h.contains("类别") && !h.contains("类型")) {
                cols.putIfAbsent("exam_score", i);
            }
        }
        if (!cols.containsKey("student_id")) {
            throw new IllegalArgumentException("表头缺少“学号”列");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = headerIdx + 1; i < grid.size(); i++) {
            List<String> line = grid.get(i);
            String sid = cell(line, cols.get("student_id"));
            if (sid.isEmpty()) {
                continue;
            }
            Map<String, String> r = new LinkedHashMap<>();
            r.put("row_no", String.valueOf(i + 1));
            r.put("student_id", sid);
            r.put("usual_score", cell(line, cols.get("usual_score")));
            r.put("exam_score", cell(line, cols.get("exam_score")));
            r.put("score_level", cell(line, cols.get("score_level")));
            r.put("exam_flag", cell(line, cols.get("exam_flag")));
            r.put("usual_ratio", cell(line, cols.get("usual_ratio")));
            rows.add(r);
        }
        return rows;
    }

    private static String cell(List<String> line, Integer idx) {
        if (idx == null || idx >= line.size()) {
            return "";
        }
        return line.get(idx) == null ? "" : line.get(idx).trim();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replace("\u00A0", " ").replace(" ", "").trim();
    }

    // ---------- xlsx template export ----------

    /**
     * 按课堂生成标准 .xlsx 成绩录入模板（不含任何第三方依赖，Excel/WPS/Numbers 均可直接打开并回传导入）。
     * 第 1 行为课堂号/课程/学期说明，第 2 行为表头（学号、姓名、班级、平时成绩、考试成绩、考试情况），
     * 平时与期末占比在系统按课堂统一填一次即可，模板中不重复列占比。
     */
    @SuppressWarnings("unchecked")
    public static byte[] buildTemplateXlsx(Map<String, Object> roster) {
        String lessonNo = strVal(roster.get("lesson_no"));
        String courseName = strVal(roster.get("course_name"));
        String term = strVal(roster.get("term"));
        int defaultScoreType = roster.get("default_score_type") instanceof Number n ? n.intValue() : 1;
        List<Map<String, Object>> allStudents = (List<Map<String, Object>>) roster.getOrDefault("students", List.of());

        List<Map<String, Object>> editable = new ArrayList<>();
        for (Map<String, Object> s : allStudents) {
            Object stObj = s.get("score_status");
            int st = stObj instanceof Number n ? n.intValue() : -1;
            if (st < 1 || st > 3) {
                editable.add(s);
            }
        }
        List<Map<String, Object>> targetRows = editable.isEmpty() ? allStudents : editable;

        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<cols>")
                .append("<col min=\"1\" max=\"1\" width=\"16\" customWidth=\"1\"/>")
                .append("<col min=\"2\" max=\"2\" width=\"16\" customWidth=\"1\"/>")
                .append("<col min=\"3\" max=\"3\" width=\"14\" customWidth=\"1\"/>")
                .append("<col min=\"4\" max=\"4\" width=\"24\" customWidth=\"1\"/>")
                .append("<col min=\"5\" max=\"5\" width=\"14\" customWidth=\"1\"/>")
                .append("<col min=\"6\" max=\"6\" width=\"16\" customWidth=\"1\"/>")
                .append("</cols>")
                .append("<sheetData>");

        int rowNum = 1;
        appendStringRow(sheet, rowNum++, List.of(
                "课堂号", lessonNo, "课程名称", courseName, "学期", term));

        if (defaultScoreType == 1) {
            appendStringRow(sheet, rowNum++, List.of("学号", "姓名", "班级", "平时成绩", "考试成绩", "考试情况"));
        } else {
            appendStringRow(sheet, rowNum++, List.of("学号", "姓名", "班级", "等级成绩", "考试情况"));
        }

        for (Map<String, Object> s : targetRows) {
            sheet.append("<row r=\"").append(rowNum).append("\">");
            int col = 0;
            appendStringCell(sheet, col++, rowNum, strVal(s.get("student_id")));
            appendStringCell(sheet, col++, rowNum, strVal(s.get("student_name")));
            appendStringCell(sheet, col++, rowNum, strVal(s.get("class_name")));
            if (defaultScoreType == 1) {
                appendNumberOrBlankCell(sheet, col++, rowNum, s.get("usual_score"));
                appendNumberOrBlankCell(sheet, col++, rowNum, s.get("exam_score"));
            } else {
                appendStringCell(sheet, col++, rowNum, strVal(s.get("score_level")));
            }
            int flag = s.get("exam_flag") instanceof Number n ? n.intValue() : 0;
            String flagText = switch (flag) {
                case 1 -> "缺考";
                case 2 -> "缓考";
                case 3 -> "违纪";
                default -> "正常";
            };
            appendStringCell(sheet, col, rowNum, flagText);
            sheet.append("</row>");
            rowNum++;
        }
        sheet.append("</sheetData></worksheet>");

        String contentTypes = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
                """;
        String rootRels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """;
        String workbook = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="成绩录入表" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
                """;
        String wbRels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
                """;

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            putEntry(zos, "[Content_Types].xml", contentTypes);
            putEntry(zos, "_rels/.rels", rootRels);
            putEntry(zos, "xl/workbook.xml", workbook);
            putEntry(zos, "xl/_rels/workbook.xml.rels", wbRels);
            putEntry(zos, "xl/worksheets/sheet1.xml", sheet.toString());
            zos.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("生成 Excel 模板失败：" + e.getMessage(), e);
        }
    }

    private static void putEntry(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static void appendStringRow(StringBuilder sb, int rowNum, List<String> values) {
        sb.append("<row r=\"").append(rowNum).append("\">");
        for (int c = 0; c < values.size(); c++) {
            appendStringCell(sb, c, rowNum, values.get(c));
        }
        sb.append("</row>");
    }

    private static void appendStringCell(StringBuilder sb, int colIdx, int rowNum, String text) {
        String ref = colLetter(colIdx) + rowNum;
        sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>")
                .append(escapeXml(text == null ? "" : text))
                .append("</t></is></c>");
    }

    private static void appendNumberOrBlankCell(StringBuilder sb, int colIdx, int rowNum, Object val) {
        if (val == null) {
            return;
        }
        String text = String.valueOf(val).trim();
        if (text.isEmpty()) {
            return;
        }
        String ref = colLetter(colIdx) + rowNum;
        sb.append("<c r=\"").append(ref).append("\"><v>")
                .append(escapeXml(text))
                .append("</v></c>");
    }

    private static String colLetter(int idx) {
        StringBuilder sb = new StringBuilder();
        int n = idx + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    private static String strVal(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
