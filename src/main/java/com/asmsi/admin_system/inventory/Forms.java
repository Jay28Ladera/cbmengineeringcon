package com.asmsi.admin_system.inventory;

import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Small helpers for reading form values. */
final class Forms {
    private Forms() {}

    static String user() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

    static boolean blank(String s) { return s == null || s.isBlank(); }

    static String cut(String s, int n) {
        if (blank(s)) return null;
        String t = s.trim();
        return t.length() > n ? t.substring(0, n) : t;
    }

    static BigDecimal num(String s) {
        if (blank(s)) return null;
        try { return new BigDecimal(s.trim().replace(",", "")); } catch (NumberFormatException e) { return null; }
    }

    static Long id(String s) {
        if (blank(s)) return null;
        try { return Long.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    static LocalDate date(String s) {
        if (blank(s)) return null;
        try { return LocalDate.parse(s.trim()); } catch (Exception e) { return null; }
    }

    static String at(List<String> l, int i) { return l == null || i >= l.size() ? null : l.get(i); }

    static int size(List<?>... lists) { int n = 0; for (List<?> l : lists) if (l != null) n = Math.max(n, l.size()); return n; }

    /** Line rows posted as parallel lists (line_material[], line_desc[], …). */
    static List<PurchasingService.LineInput> lines(List<String> material, List<String> desc, List<String> qty, List<String> unit, List<String> price) {
        List<PurchasingService.LineInput> out = new ArrayList<>();
        for (int i = 0; i < size(material, desc, qty, unit, price); i++)
            out.add(new PurchasingService.LineInput(id(at(material, i)), at(desc, i), num(at(qty, i)), at(unit, i), num(at(price, i))));
        return out;
    }
}
