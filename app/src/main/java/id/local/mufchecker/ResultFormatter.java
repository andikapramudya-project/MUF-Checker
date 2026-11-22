package id.local.mufchecker;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResultFormatter {
    private ResultFormatter() {}
    public static String status(String original) {
        if ("TERINDIKASI SUDAH BAYAR".equals(original) || "SUDAH BAYAR".equals(original)) return "SUDAH BAYAR";
        if ("TERINDIKASI BELUM BAYAR".equals(original) || "JATUH TEMPO TERTINGGAL".equals(original) || "BELUM BAYAR".equals(original)) return "BELUM BAYAR";
        return "ERROR";
    }
    public static String cleanName(String raw) {
        String value = raw == null ? "" : raw;
        Pattern entity = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);|&(amp|nbsp|lt|gt|quot|apos);");
        for (int pass = 0; pass < 2; pass++) {
            Matcher m = entity.matcher(value); StringBuffer out = new StringBuffer();
            while (m.find()) {
                String decoded = m.group();
                try {
                    if (m.group(1) != null) {
                        String number = m.group(1); int point = Integer.parseInt(number.startsWith("x") ? number.substring(1) : number, number.startsWith("x") ? 16 : 10);
                        if (Character.isValidCodePoint(point) && !(point >= 0xD800 && point <= 0xDFFF) && point >= 32) decoded = new String(Character.toChars(point));
                    } else {
                        String key = m.group(2);
                        decoded = key.equals("amp") ? "&" : key.equals("nbsp") ? " " : key.equals("lt") ? "<" : key.equals("gt") ? ">" : key.equals("quot") ? "\"" : "'";
                    }
                } catch (RuntimeException ignored) { }
                m.appendReplacement(out, Matcher.quoteReplacement(decoded));
            }
            m.appendTail(out); value = out.toString();
        }
        return display(value.replaceAll("[\\p{Z}\\s]+", " ").trim());
    }
    public static String display(String value) { return value == null || value.trim().isEmpty() || value.equals("[AMBIGU]") ? "—" : value.trim(); }
    public static String checked(String value) {
        try { return ZonedDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM HH:mm")); }
        catch (RuntimeException ignored) {
            try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss 'WITA'")).format(DateTimeFormatter.ofPattern("dd/MM HH:mm")); }
            catch (RuntimeException e) { return "—"; }
        }
    }
    public static String compact(String status, String contract, String name, String installment, String amount, String checked) {
        return status(status) + "\n\nKontrak: " + display(contract) + "\nNama: " + cleanName(name)
            + "\nCicilan ke: " + display(installment) + "\nAngsuran: " + display(amount) + "\n\nDiperiksa " + checked(checked);
    }
}
