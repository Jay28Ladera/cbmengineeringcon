package com.asmsi.admin_system.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Small form-reading helpers for the Accounting pages. */
final class F {
    private F() {}
    static boolean blank(String s) { return s == null || s.isBlank(); }
    static String cut(String s, int n) { if (s == null) return null; s = s.trim(); return s.isEmpty() ? null : (s.length() <= n ? s : s.substring(0, n)); }
    static BigDecimal num(String s) {
        if (blank(s)) return null;
        try { return new BigDecimal(s.replace(",", "").replace("₱", "").trim()); } catch (NumberFormatException e) { return null; }
    }
    static Long id(String s) { try { return blank(s) ? null : Long.valueOf(s.trim()); } catch (NumberFormatException e) { return null; } }
    static LocalDate date(String s) { try { return blank(s) ? null : LocalDate.parse(s.trim()); } catch (Exception e) { return null; } }
    static String at(List<String> l, int i) { return l == null || i >= l.size() ? null : l.get(i); }
    static int size(List<?>... lists) { int n = 0; for (List<?> l : lists) if (l != null) n = Math.max(n, l.size()); return n; }
}
