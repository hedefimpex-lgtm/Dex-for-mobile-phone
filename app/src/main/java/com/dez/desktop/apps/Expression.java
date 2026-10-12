package com.dez.desktop.apps;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Recursive-descent evaluator for + − × ÷ %, parentheses and unary minus. */
final class Expression {
    private static final MathContext MC = MathContext.DECIMAL64;
    private final String s;
    private int pos;

    private Expression(String s) {
        this.s = s.replace('×', '*').replace('÷', '/').replace('−', '-').replace(',', '.')
                .replace(" ", "");
    }

    static BigDecimal eval(String text) {
        Expression e = new Expression(text);
        BigDecimal v = e.sum();
        if (e.pos != e.s.length()) throw new ArithmeticException("Unexpected '" + e.s.charAt(e.pos) + "'");
        return v;
    }

    static String format(BigDecimal v) {
        BigDecimal r = v.round(new MathContext(14, RoundingMode.HALF_UP)).stripTrailingZeros();
        if (r.compareTo(BigDecimal.ZERO) == 0) return "0";
        return r.abs().compareTo(new BigDecimal("1e15")) >= 0 ? r.toString() : r.toPlainString();
    }

    private BigDecimal sum() {
        BigDecimal v = product();
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '+') {
                pos++;
                v = v.add(product(), MC);
            } else if (c == '-') {
                pos++;
                v = v.subtract(product(), MC);
            } else {
                break;
            }
        }
        return v;
    }

    private BigDecimal product() {
        BigDecimal v = unary();
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '*') {
                pos++;
                v = v.multiply(unary(), MC);
            } else if (c == '/') {
                pos++;
                BigDecimal d = unary();
                if (d.signum() == 0) throw new ArithmeticException("Division by zero");
                v = v.divide(d, MC);
            } else {
                break;
            }
        }
        return v;
    }

    private BigDecimal unary() {
        if (pos < s.length() && s.charAt(pos) == '-') {
            pos++;
            return unary().negate();
        }
        if (pos < s.length() && s.charAt(pos) == '+') {
            pos++;
            return unary();
        }
        BigDecimal v = atom();
        while (pos < s.length() && s.charAt(pos) == '%') {
            pos++;
            v = v.divide(BigDecimal.valueOf(100), MC);
        }
        return v;
    }

    private BigDecimal atom() {
        if (pos >= s.length()) throw new ArithmeticException("Incomplete expression");
        if (s.charAt(pos) == '(') {
            pos++;
            BigDecimal v = sum();
            if (pos < s.length() && s.charAt(pos) == ')') pos++;
            return v;
        }
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
        if (start == pos) throw new ArithmeticException("Number expected");
        return new BigDecimal(s.substring(start, pos));
    }
}
