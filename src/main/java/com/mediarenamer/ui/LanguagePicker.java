package com.mediarenamer.ui;

import com.mediarenamer.app.I18n;

import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * Searchable language selector: every language and language-country pair the JDK knows, shown by its name in the
 * interface language ("Español (España)") with its tag on the side. Typing filters the list by name or tag (case and
 * accents ignored); Enter, a click or leaving the field picks the match, and text that matches nothing goes back to the
 * previous value. The first entry is the empty value, whose text is given by the caller ("(don't change)", "Automatic").
 */
final class LanguagePicker extends JComboBox<LanguagePicker.Item> {

    /** A language: tag (null = the empty value) and the text shown. */
    record Item(String tag, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final List<Item> all = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private Item value;
    /** True while the list is being replaced: the combo events of that change are not the user's choice. */
    private boolean filtering;

    LanguagePicker(String emptyLabel) {
        all.add(new Item(null, emptyLabel));
        all.addAll(languages(I18n.locale()));
        value = all.get(0);
        setModel(model(all, value));
        setEditable(true);
        setMaximumRowCount(14);
        setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                // names and tags come from the JDK locale data, not from the user: safe to show as HTML
                String text = v instanceof Item it && it.tag() != null
                        ? "<html>" + it.label() + "&nbsp;&nbsp;<span style='color:" + hex(s ? l.getSelectionForeground() : Theme.secondaryText()) + "'>"
                                + it.tag() + "</span></html>"
                        : String.valueOf(v);
                return super.getListCellRendererComponent(l, text, i, s, f);
            }
        });
        JTextField editor = editorField();
        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // Escape gives up the search: the value stays as it was (runs before the combo closes its popup)
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    editor.setText(value.label());
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (!navigationKey(e.getKeyCode())) {
                    filter(editor.getText());
                }
            }
        });
        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                editor.selectAll();
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (!e.isTemporary()) {
                    commit();
                }
            }
        });
        addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                if (!filtering) {
                    SwingUtilities.invokeLater(LanguagePicker.this::commit);
                }
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        addActionListener(e -> {
            if (!filtering && getSelectedItem() instanceof Item it) {
                choose(it);
            }
        });
    }

    /** Selected tag; null = the empty value. */
    String getTag() {
        return value.tag();
    }

    /** Selects a tag; one that is not in the list (a hand-written one) is added to it. Blank = the empty value. */
    void setTag(String tag) {
        Item it = find(tag);
        if (it == null) {
            Locale l = Locale.forLanguageTag(tag.trim());
            it = new Item(tag.trim(), capitalize(l.getDisplayName(I18n.locale()), I18n.locale()));
            all.add(it);
        }
        value = it;
        restore();
    }

    /** Runs r whenever the selected language changes. */
    void onChange(Runnable r) {
        listeners.add(r);
    }

    private Item find(String tag) {
        if (tag == null || tag.isBlank()) {
            return all.get(0);
        }
        String wanted = Locale.forLanguageTag(tag.trim()).toLanguageTag();
        return all.stream().filter(i -> i.tag() != null && (i.tag().equalsIgnoreCase(tag.trim()) || i.tag().equals(wanted))).findFirst().orElse(null);
    }

    private JTextField editorField() {
        return (JTextField) getEditor().getEditorComponent();
    }

    /** Shows only the entries that match the typed text, keeping the text and the caret where they are. */
    void filter(String text) {
        JTextField editor = editorField();
        int caret = editor.getCaretPosition();
        List<Item> matches = matches(all, text);
        filtering = true;
        try {
            setModel(model(matches, null));
            editor.setText(text);
            editor.setCaretPosition(Math.min(caret, text.length()));
            if (isShowing()) {
                // an empty list keeps its (empty) popup: hiding it would pick a value while the user is still typing
                showPopup();
            }
        } finally {
            filtering = false;
        }
    }

    /** Picks the entry for the typed text (or the previous value if nothing matches) and shows the whole list again. */
    void commit() {
        String text = editorField().getText();
        Object selected = getSelectedItem();
        // an emptied field is the empty value
        Item chosen = text.isBlank() ? all.get(0) : selected instanceof Item it && it.label().equals(text) ? it : resolve(all, text);
        if (chosen != null) {
            choose(chosen);
        }
        restore();
    }

    private void choose(Item it) {
        if (!it.equals(value)) {
            value = it;
            listeners.forEach(Runnable::run);
        }
    }

    private void restore() {
        filtering = true;
        try {
            setModel(model(all, value));
            editorField().setText(value.label());
        } finally {
            filtering = false;
        }
    }

    private static DefaultComboBoxModel<Item> model(List<Item> items, Item selected) {
        DefaultComboBoxModel<Item> m = new DefaultComboBoxModel<>();
        m.addAll(items);
        m.setSelectedItem(selected);
        return m;
    }

    private static boolean navigationKey(int code) {
        return switch (code) {
            case KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE, KeyEvent.VK_TAB,
                    KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT ->
                true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ list and search (static: unit tested)

    /**
     * Languages offered: plain ISO 639-1 languages and language-country pairs, without scripts, variants, extensions
     * or region numbers (the data services take tags like {@code es} or {@code es-ES}), sorted by name.
     */
    static List<Item> languages(Locale display) {
        List<Locale> locales = new ArrayList<>();
        Set<String> tags = new HashSet<>();
        for (Locale l : Locale.getAvailableLocales()) {
            if (l.getLanguage().length() == 2 && l.getScript().isEmpty() && l.getVariant().isEmpty() && !l.hasExtensions()
                    && (l.getCountry().isEmpty() || l.getCountry().matches("[A-Z]{2}")) && tags.add(l.toLanguageTag())) {
                locales.add(l);
            }
        }
        List<Item> items = new ArrayList<>();
        Set<String> labels = new HashSet<>();
        for (Locale l : locales) {
            items.add(new Item(l.toLanguageTag(), capitalize(l.getDisplayName(display), display)));
        }
        Collator collator = Collator.getInstance(display);
        items.sort((a, b) -> collator.compare(a.label(), b.label()));
        // the same name for two tags (old and new codes): the tag tells them apart
        List<Item> unique = new ArrayList<>();
        for (Item it : items) {
            unique.add(labels.add(it.label()) ? it : new Item(it.tag(), it.label() + " (" + it.tag() + ")"));
        }
        return unique;
    }

    /** Entries whose name or tag contains the text (case and accents ignored); blank = all. */
    static List<Item> matches(List<Item> items, String text) {
        String q = normalize(text);
        if (q.isEmpty()) {
            return items;
        }
        return items.stream().filter(i -> normalize(i.label()).contains(q) || i.tag() != null && normalize(i.tag()).contains(q)).toList();
    }

    /**
     * Entry for typed text: the same name or tag, else the first name that starts with it, else the first match; null
     * if nothing matches (the caller keeps its value).
     */
    static Item resolve(List<Item> items, String text) {
        String q = normalize(text);
        if (q.isEmpty()) {
            return null;
        }
        for (Item i : items) {
            if (normalize(i.label()).equals(q) || i.tag() != null && normalize(i.tag()).equals(q)) {
                return i;
            }
        }
        for (Item i : items) {
            if (i.tag() != null && normalize(i.label()).startsWith(q)) {
                return i;
            }
        }
        return matches(items, text).stream().filter(i -> i.tag() != null).findFirst().orElse(null);
    }

    static String normalize(String s) {
        return s == null ? "" : Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private static String capitalize(String s, Locale l) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(l) + s.substring(1);
    }

    private static String hex(java.awt.Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
