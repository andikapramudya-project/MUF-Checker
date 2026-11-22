package id.local.mufchecker;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Uses labelled, spatially paired fields instead of guessing dates anywhere on screen. */
public final class BillReader {
    private BillReader() {}
    public static final List<String> LABELS = Arrays.asList("jatuh tempo", "nomor pelanggan", "nomor kontrak", "nama",
        "cicilan ke", "total cicilan", "total tagihan", "tagihan", "biaya admin", "total pembayaran");
    public static final class Cell {
        public final String text;
        public final int left, top, right, bottom;
        public Cell(String text, int left, int top, int right, int bottom) {
            this.text = text; this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        int centerY() { return (top + bottom) / 2; }
    }
    public static String labelOf(String text) {
        String s = InquiryRules.normalized(text).replaceAll("[:.]$", "");
        return LABELS.contains(s) ? s : "";
    }
    public static Map<String, String> fields(List<Cell> cells) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Cell label : cells) {
            String key = labelOf(label.text);
            if (key.isEmpty()) {
                String raw = label.text.trim();
                // Android sometimes exposes the whole label/value row as a single node.
                for (String candidate : LABELS) {
                    if (raw.toLowerCase(java.util.Locale.ROOT).startsWith(candidate)) {
                        String rest = raw.substring(candidate.length());
                        if (rest.startsWith(":" ) || rest.startsWith("\n")) put(out, candidate, rest.replaceFirst("^[:\\s]+", ""));
                    }
                }
                continue;
            }
            Cell best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (Cell value : cells) {
                if (value == label || !labelOf(value.text).isEmpty() || value.left < label.right - 5) continue;
                int dy = Math.abs(value.centerY() - label.centerY());
                if (dy > Math.max(16, Math.max(label.bottom - label.top, value.bottom - value.top) / 2)) continue;
                int distance = dy * 20 + Math.max(0, value.left - label.right);
                if (distance < bestDistance) { best = value; bestDistance = distance; }
            }
            if (best != null) put(out, key, best.text);
        }
        return out;
    }
    private static void put(Map<String, String> map, String key, String value) {
        if (value.trim().isEmpty()) return;
        if (map.containsKey(key) && !map.get(key).equals(value.trim())) map.put(key, "[AMBIGU]");
        else map.put(key, value.trim());
    }
    public static LocalDate verifiedDue(Map<String, String> fields, String expected) {
        String identity = fields.get("nomor pelanggan");
        if (identity == null) identity = fields.get("nomor kontrak");
        if (!InquiryRules.identityMatches(expected, identity)) throw new IllegalArgumentException("Identitas kontrak pada rincian belum cocok atau belum terbaca");
        return InquiryRules.parseDate(fields.get("jatuh tempo"));
    }
    public static boolean earlyDetailsComplete(Map<String,String> fields){
        for(String key:new String[]{"nama","tagihan"}){
            String value=fields.get(key);
            if(value==null||value.trim().isEmpty()||value.equals("[AMBIGU]"))return false;
        }
        return true;
    }
}
