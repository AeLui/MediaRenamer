package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediarenamer.app.I18n;
import com.mediarenamer.format.ExpressionBinding;
import com.mediarenamer.format.MediaBindingBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

/** The "Variables" tooltip of the format editor lists every variable, and nothing that does not exist. */
class FormatVariablesTest {

    private static final ResourceBundle BASE = ResourceBundle.getBundle("com.mediarenamer.i18n.messages", Locale.ROOT);

    @Test
    void tooltipListsExactlyTheDefinedVariables() {
        Set<String> defined = new ExpressionBinding(new MediaBindingBean(null, null), null).getDefinedNames();
        Set<String> listed = new TreeSet<>();
        for (List<String> names : FormatDialog.VARIABLES.values()) {
            for (String n : names) {
                // "plex.id", "video[0].Format": the variable is the part before the property
                listed.add(n.split("[.\\[]")[0]);
            }
        }
        List<String> unknown = new ArrayList<>(listed);
        unknown.removeAll(defined);
        assertEquals(List.of(), unknown, "listed but not defined");
        List<String> missing = new ArrayList<>(new TreeSet<>(defined));
        missing.removeAll(listed);
        assertEquals(List.of(), missing, "defined but not listed");
    }

    @Test
    void groupTitlesAreTranslatedAndTheTooltipIsHtml() {
        for (String group : FormatDialog.VARIABLES.keySet()) {
            assertTrue(BASE.containsKey(group), group);
        }
        I18n.setLocale(Locale.forLanguageTag("es"));
        try {
            String tip = FormatDialog.variablesTooltip();
            assertTrue(tip.startsWith("<html>") && tip.contains("Identificación") && tip.contains("{s00e00}") && tip.contains("{plex.id}"), tip);
        } finally {
            I18n.setLocale(Locale.ENGLISH);
        }
    }
}
