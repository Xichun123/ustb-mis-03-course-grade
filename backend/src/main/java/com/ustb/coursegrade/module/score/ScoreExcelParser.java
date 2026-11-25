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

/**
 * 成绩表格解析器：支持 .xlsx（Zip + XML，不引入额外依赖）与 .csv。
 * 输出统一为 [{row_no, student_id, usual_score, exam_score, exam_flag, usual_ratio}]。
 */
public final class ScoreExcelParser {

    private ScoreExcelParser() {
    }

    public static List<Map<String, String>> parse(InputStream in, String filename) throws Exception {
        byte[] data = in.readAllBytes();
        if (data.length == 0) {
            throw new IllegalArgumentException("文件为空");
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
            if (h.contains("学号") && !cols.containsKey("student_id")) {
                cols.put("student_id", i);
            } else if (h.contains("平时") && h.contains("成绩")) {
                cols.put("usual_score", i);
            } else if (h.contains("期末") || h.contains("考试") && !h.contains("情况")) {
                cols.putIfAbsent("exam_score", i);
            } else if (h.contains("考试情况") || h.equals("缺考")) {
                cols.put("exam_flag", i);
            } else if (h.contains("占比") || h.contains("比例")) {
                cols.put("usual_ratio", i);
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
}
