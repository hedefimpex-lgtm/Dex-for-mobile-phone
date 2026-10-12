package com.dez.desktop;

import android.graphics.drawable.GradientDrawable;

/** Built-in gradient wallpapers. */
public final class Wallpapers {
    private static final int[][] GRADIENTS = {
            {0xFF0B1E3F, 0xFF1F5FA8, 0xFF5BA4E6},
            {0xFF1A1033, 0xFF3A1C71, 0xFFD76D77},
            {0xFF0F2027, 0xFF203A43, 0xFF2C5364},
            {0xFF1A2980, 0xFF1F7A9C, 0xFF26D0CE},
            {0xFF141517, 0xFF232526, 0xFF414345},
            {0xFF0F0C29, 0xFF302B63, 0xFF24243E},
            {0xFF134E5E, 0xFF3B8D72, 0xFF71B280},
    };

    private Wallpapers() {}

    public static int count() {
        return GRADIENTS.length;
    }

    public static GradientDrawable create(int index) {
        int[] colors = GRADIENTS[Math.max(0, Math.min(index, GRADIENTS.length - 1))];
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
    }
}
