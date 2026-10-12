package com.dez.desktop.apps;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dez.desktop.DesktopActivity;
import com.dez.desktop.R;
import com.dez.desktop.Ui;

final class CalculatorApp extends WindowApp {
    CalculatorApp() {
        super(BuiltInApps.CALCULATOR, R.string.app_calculator, R.drawable.ic_app_calc);
    }

    @Override
    public View createView(DesktopActivity host, Bundle args) {
        return new CalculatorView(host);
    }

    static final class CalculatorView extends LinearLayout {
        private static final String[] KEYS = {
                "C", "(", ")", "÷",
                "7", "8", "9", "×",
                "4", "5", "6", "−",
                "1", "2", "3", "+",
                "%", "0", ".", "=",
        };

        private final TextView expr;
        private final TextView result;
        private boolean justEvaluated;

        CalculatorView(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setBackgroundColor(Ui.WINDOW_BG);
            int pad = Ui.dp(c, 12);
            setPadding(pad, pad, pad, pad);

            expr = Ui.text(c, "", 26, Ui.TEXT);
            expr.setGravity(Gravity.END | Gravity.BOTTOM);
            expr.setSingleLine(true);
            expr.setEllipsize(TextUtils.TruncateAt.START);
            addView(expr, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
            result = Ui.text(c, "", 18, Ui.TEXT_DIM);
            result.setGravity(Gravity.END);
            result.setSingleLine(true);
            addView(result, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            LinearLayout backRow = new LinearLayout(c);
            backRow.setGravity(Gravity.END);
            backRow.addView(Ui.iconButton(c, R.drawable.ic_back, 40, 10, "⌫", v -> backspace()));
            addView(backRow);

            GridLayout grid = new GridLayout(c);
            grid.setColumnCount(4);
            for (String k : KEYS) {
                TextView b = Ui.button(c, k, v -> press(k));
                b.setTextSize(18);
                if (k.equals("=")) b.setBackground(Ui.ripple(c, Ui.ACCENT, 8));
                else if ("÷×−+()%C".contains(k)) b.setTextColor(0xFF8AB4FF);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
                lp.width = 0;
                lp.height = 0;
                int m = Ui.dp(c, 3);
                lp.setMargins(m, m, m, m);
                grid.addView(b, lp);
            }
            addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 3f));
        }

        private void backspace() {
            CharSequence t = expr.getText();
            if (t.length() > 0) expr.setText(t.subSequence(0, t.length() - 1));
            preview();
        }

        private void press(String k) {
            switch (k) {
                case "C":
                    expr.setText("");
                    result.setText("");
                    break;
                case "=":
                    try {
                        String r = Expression.format(Expression.eval(expr.getText().toString()));
                        expr.setText(r);
                        result.setText("");
                        justEvaluated = true;
                    } catch (RuntimeException e) {
                        result.setText(R.string.calc_error);
                    }
                    return;
                default:
                    boolean digit = Character.isDigit(k.charAt(0)) || k.equals(".") || k.equals("(");
                    if (justEvaluated && digit) expr.setText("");
                    expr.append(k);
                    preview();
            }
            justEvaluated = false;
        }

        private void preview() {
            try {
                String text = expr.getText().toString();
                result.setText(text.isEmpty() ? "" : "= " + Expression.format(Expression.eval(text)));
            } catch (RuntimeException e) {
                result.setText("");
            }
        }
    }
}
