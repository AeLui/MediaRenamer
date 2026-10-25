package com.mediarenamer.ui;

import com.mediarenamer.app.Logging;
import com.mediarenamer.app.Settings;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.util.Map;

import javax.swing.UIManager;

/** Visual theme: FlatLaf with the logo violet as accent color, rounded corners and dark mode. */
public final class Theme {

    public static final Color ACCENT = Logo.VIOLET;
    public static final Color ACCENT_2 = Logo.CYAN;
    public static final Color SUCCESS = new Color(0x16A34A);
    public static final Color ERROR = new Color(0xDC2626);
    public static final Color WARNING = new Color(0xD97706);
    /** Category or profile with unsaved changes (Preferences, Profiles). */
    public static final Color MODIFIED = WARNING;

    private Theme() {
    }

    public static void install() {
        FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", "#7C3AED"));
        apply(Settings.get().getBoolean("ui.dark", false));
    }

    public static void apply(boolean dark) {
        try {
            if (dark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Exception e) {
            Logging.warn("Look and feel: " + e.getMessage());
        }
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("CheckBox.arc", 6);
        UIManager.put("ProgressBar.arc", 999);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 11);
        UIManager.put("ScrollPane.smoothScrolling", true);
        UIManager.put("List.selectionArc", 10);
        UIManager.put("List.selectionInsets", new Insets(1, 4, 1, 4));
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("TitlePane.showIcon", true);
        UIManager.put("ToolTip.border", javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8));
        Font f = UIManager.getFont("defaultFont");
        if (f != null) {
            UIManager.put("defaultFont", f.deriveFont(13f));
        }
    }

    /** Switches light/dark on the fly and saves the preference. */
    public static void toggle() {
        boolean dark = !isDark();
        Settings.get().put("ui.dark", String.valueOf(dark));
        apply(dark);
        FlatLaf.updateUI();
    }

    public static boolean isDark() {
        return FlatLaf.isLafDark();
    }

    /** Background of the "cards" (lists). */
    public static Color card() {
        return UIManager.getColor("List.background");
    }

    /** General background, slightly different from the cards. */
    public static Color surface() {
        Color c = UIManager.getColor("Panel.background");
        return isDark() ? c : mix(c, new Color(0xEEF0F7), 0.6);
    }

    public static Color secondaryText() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c == null ? Color.GRAY : c;
    }

    public static Color border() {
        Color c = UIManager.getColor("Component.borderColor");
        return c == null ? Color.LIGHT_GRAY : c;
    }

    /** Soft version of the accent for chip backgrounds. */
    public static Color accentSoft() {
        return isDark() ? new Color(0x3B2A66) : new Color(0xEDE7FE);
    }

    /** Accent-colored text with enough contrast in both themes. */
    public static Color accentText() {
        return isDark() ? new Color(0xC4B5FD) : ACCENT;
    }

    public static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t), (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }
}
