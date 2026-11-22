package id.local.mufchecker;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure rules shared by the Android reader and the offline regression tests. */
public final class InquiryRules {
    private InquiryRules() {}
    public static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
    public static boolean validContract(String value) { return value != null && value.matches("[0-9]{8,20}"); }
    public static boolean isProvider(String value) { return normalized(value).equals("mandiri utama finance"); }
    public static boolean isInquiryButton(String value) {
        String s = normalized(value);
        return s.equals("bayar") || s.equals("cek tagihan") || s.equals("cek");
    }
    public static boolean maySubmit(boolean provider, boolean inputPage, boolean checkout,
                                    String expected, String field, boolean alreadySubmitted) {
        return provider && inputPage && !checkout && validContract(expected)
                && expected.equals(field) && !alreadySubmitted;
    }
    public static boolean identityMatches(String expected, String displayed) {
        if (!validContract(expected) || displayed == null) return false;
        String s = displayed.toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]", "");
        if (s.matches("[0-9]+")) return s.equals(expected);
        if (!s.matches("[0-9*X•●]+") || !s.matches(".*[*X•●].*")) return false;
        int visible = s.replaceAll("[^0-9]", "").length();
        if (visible < 6) return false;
        // A channel may use a fixed number of asterisks, independent of hidden length.
        String regex = s.replaceAll("[*X•●]+", "[0-9]+");
        return expected.matches(regex);
    }
    public static LocalDate parseDate(String value) {
        if (value == null) throw new IllegalArgumentException("Tanggal tidak tersedia");
        String v = value.trim().toUpperCase(Locale.ROOT);
        Matcher m = Pattern.compile("(?<![0-9])([0-9]{1,2})[-/\\s]+([A-Z]+|[0-9]{1,2})[-/\\s]+([0-9]{4}|[0-9]{2})(?![0-9])").matcher(v);
        if (!m.find()) throw new IllegalArgumentException("Format tanggal belum dikenali");
        int day = Integer.parseInt(m.group(1));
        String mon = m.group(2);
        int year = Integer.parseInt(m.group(3));
        if (year < 100) year += 2000;
        int month;
        if (mon.matches("[0-9]+")) month = Integer.parseInt(mon);
        else {
            String[] aliases = {"JAN JANUARI JANUARY", "FEB FEBRUARI FEBRUARY", "MAR MARET MARCH", "APR APRIL",
                "MEI MAY", "JUN JUNI JUNE", "JUL JULI JULY", "AGU AGS AUG AGUSTUS AUGUST",
                "SEP SEPT SEPTEMBER", "OKT OCT OKTOBER OCTOBER", "NOV NOVEMBER", "DES DEC DESEMBER DECEMBER"};
            month = 0;
            for (int i = 0; i < aliases.length; i++) if ((" " + aliases[i] + " ").contains(" " + mon + " ")) month = i + 1;
        }
        if (m.find()) throw new IllegalArgumentException("Tanggal ambigu; ada lebih dari satu tanggal");
        if (year < 2000 || year > 2099) throw new IllegalArgumentException("Tahun di luar rentang");
        return LocalDate.of(year, month, day);
    }
    public static String status(LocalDate due, YearMonth period) {
        int cmp = YearMonth.from(due).compareTo(period);
        if (cmp > 0) return "TERINDIKASI SUDAH BAYAR";
        if (cmp == 0) return "TERINDIKASI BELUM BAYAR";
        return "JATUH TEMPO TERTINGGAL";
    }
    public static String reason(LocalDate due, YearMonth period) {
        int cmp = YearMonth.from(due).compareTo(period);
        if (cmp > 0) return "Jatuh tempo yang tampil sudah melewati periode pemeriksaan. Ini indikasi, bukan bukti pembayaran.";
        if (cmp == 0) return "Tagihan periode yang diperiksa masih tampil. Pembayaran yang baru dilakukan mungkin belum diperbarui.";
        return "Jatuh tempo yang tampil mendahului periode pemeriksaan. Perlu verifikasi tunggakan dan alokasi pembayaran.";
    }
}
