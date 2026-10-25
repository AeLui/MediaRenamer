package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.mediarenamer.app.I18n;
import com.mediarenamer.rename.Mode;
import com.mediarenamer.rename.Preset;
import com.mediarenamer.rename.Presets;

import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.Locale;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Profiles: unsaved changes survive switching profile, are marked, and Save stores all of them at once. */
class PresetsDialogTest {

    private final Preset a = new Preset(null, "Test A", "{n} ({y})", "TheMovieDB", null, null, "es-ES");
    private final Preset b = new Preset(null, "Test B", "{n} - {s00e00} - {t}", "TheMovieDB::TV", null, null, null);

    @BeforeEach
    void setUp() {
        I18n.setLocale(Locale.ENGLISH);
        Presets.get().list().forEach(Presets.get()::remove);
        Presets.get().put(a);
        Presets.get().put(b);
    }

    @AfterEach
    void cleanUp() {
        Presets.get().list().forEach(Presets.get()::remove);
    }

    @Test
    void changesAreKeptWhenSwitchingProfileAndSavedTogether() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            PresetsDialog d = new PresetsDialog(null);
            try {
                d.list.setSelectedIndex(0);
                assertEquals("Test A", d.name.getText());
                assertEquals("es-ES", d.language.getTag(), "the saved language is selected in the list");
                assertFalse(d.hasUnsavedChanges(), "showing a profile is not a change");

                d.name.setText("Test A2");
                assertTrue(d.hasUnsavedChanges());
                d.list.setSelectedIndex(1);
                assertEquals("Test B", d.name.getText());
                d.name.setText("Test B2");
                d.list.setSelectedIndex(0);
                assertEquals("Test A2", d.name.getText(), "the unsaved change came back");

                // the profile list marks both, and nothing is saved yet
                assertEquals(List.of("Test A", "Test B"), Presets.get().list().stream().map(Preset::name).toList());

                assertTrue(d.save());
                assertFalse(d.hasUnsavedChanges());
                assertEquals(List.of("Test A2", "Test B2"), Presets.get().list().stream().map(Preset::name).sorted().toList());
                assertEquals("es-ES", Presets.get().list().stream().filter(p -> p.name().equals("Test A2")).findFirst().orElseThrow().language());
            } finally {
                d.dispose();
            }
        });
    }

    @Test
    void undoingAChangeClearsTheMarkAndAnUntouchedNewProfileIsNotAChange() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            PresetsDialog d = new PresetsDialog(null);
            try {
                d.list.setSelectedIndex(1);
                d.name.setText("Other");
                assertTrue(d.hasUnsavedChanges());
                d.name.setText("Test B");
                assertFalse(d.hasUnsavedChanges(), "back to the saved value");
            } finally {
                d.dispose();
            }
        });
    }

    @Test
    void mediaTypeFiltersSourcesAndFields() {
        assertEquals(List.of(Mode.Movie, Mode.Episode, Mode.Music), PresetsDialog.TYPES);
        assertEquals(List.of("TheMovieDB"), PresetsDialog.sourcesFor(Mode.Movie));
        assertEquals(List.of("TheMovieDB::TV", "TheTVDB", "AniDB"), PresetsDialog.sourcesFor(Mode.Episode));
        assertEquals(List.of("ID3", "AcoustID"), PresetsDialog.sourcesFor(Mode.Music));
        assertTrue(PresetsDialog.applies(Mode.Episode, "order"));
        assertFalse(PresetsDialog.applies(Mode.Movie, "order"));
        assertTrue(PresetsDialog.applies(Mode.Movie, "language"));
        assertFalse(PresetsDialog.applies(Mode.Music, "matchMode"));
        assertFalse(PresetsDialog.applies(Mode.Music, "language"));
        // old "file" profiles open as movies
        assertEquals(Mode.Movie, PresetsDialog.typeOf(new Preset(null, "F", "{fn}", "file", null, null, null)));
        assertEquals(Mode.Episode, PresetsDialog.typeOf(b));
    }

    @Test
    void switchingTypeOffersItsSourcesAndHidesFieldsThatDoNotApply() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            PresetsDialog d = new PresetsDialog(null);
            try {
                d.list.setSelectedIndex(1);
                assertEquals(Mode.Episode, d.type.getSelectedItem());
                assertEquals("TheMovieDB::TV", d.database.getSelectedItem());
                assertTrue(d.order.isVisible());
                assertEquals(3, d.database.getItemCount());

                d.type.setSelectedItem(Mode.Music);
                assertEquals(2, d.database.getItemCount());
                assertTrue(List.of("ID3", "AcoustID").contains(d.database.getSelectedItem()));
                assertFalse(d.order.isVisible());
                assertFalse(d.matchMode.isVisible());
                assertFalse(d.language.isVisible());
                assertTrue(d.hasUnsavedChanges());

                d.list.setSelectedIndex(0);
                assertEquals(Mode.Movie, d.type.getSelectedItem());
                assertEquals(1, d.database.getItemCount());
                assertFalse(d.order.isVisible(), "no episode order for movies");
                assertTrue(d.language.isVisible());
                assertEquals("es-ES", d.language.getTag());

                assertTrue(d.save());
                Preset music = Presets.get().list().stream().filter(p -> p.name().equals("Test B")).findFirst().orElseThrow();
                assertEquals(Mode.Music, music.getMode());
                assertEquals(null, music.language());
            } finally {
                d.dispose();
            }
        });
    }

    @Test
    void formatEditorModeFollowsTheDataSource() {
        assertEquals(Mode.File, PresetsDialog.modeOf("file"));
        assertEquals(Mode.File, PresetsDialog.modeOf("NoSuchSource"));
        assertEquals(Mode.Movie, PresetsDialog.modeOf("TheMovieDB"));
        assertEquals(Mode.Episode, PresetsDialog.modeOf("TheMovieDB::TV"));
    }
}
