package com.mediarenamer.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MediaDetectionTest {

    @Test
    void seriesNameFromFileName() {
        assertEquals("The Walking Dead", MediaDetection.detectSeriesName(new File("The.Walking.Dead.S05E01.720p.HDTV.x264-KILLERS.mkv")));
        assertEquals("Breaking Bad", MediaDetection.detectSeriesName(new File("Breaking Bad - 1x01 - Pilot.avi")));
        assertEquals("Dark", MediaDetection.detectSeriesName(new File("Dark.S01E01.1080p.WEB-DL.DUAL.mkv")));
    }

    @Test
    void seriesNameFromFolder() {
        File f = new File("D:/Series/The Office/Season 2/S02E03.mkv");
        assertEquals("The Office", MediaDetection.detectSeriesName(f));
    }

    @Test
    void movieWithYear() {
        MediaDetection.MovieQuery q = MediaDetection.detectMovie(new File("Mufasa.2024.WEB-DL.4K.SDR.mkv"));
        assertEquals("Mufasa", q.name());
        assertEquals(2024, q.year());

        q = MediaDetection.detectMovie(new File("3_Pequeños_Ninjas_1992_WEBRip_m1080p_h264_AC3_Spa_Eng_+Sub.mp4"));
        assertEquals("3 Pequeños Ninjas", q.name());
        assertEquals(1992, q.year());

        q = MediaDetection.detectMovie(new File("Blade Runner (1982) [1080p].mkv"));
        assertEquals("Blade Runner", q.name());
        assertEquals(1982, q.year());
    }

    @Test
    void movieFromFolderWhenFileIsGeneric() {
        MediaDetection.MovieQuery q = MediaDetection.detectMovie(new File("C:/Pelis/Alien (1979)/movie.mkv"));
        assertEquals("Alien", q.name());
        assertEquals(1979, q.year());
    }

    @Test
    void stripReleaseInfo() {
        assertEquals("Movie Title 2010", MediaDetection.stripReleaseInfo("Movie.Title.2010.1080p.BluRay.x264.DTS-HD.MA.5.1-FGT.mkv", false));
    }

    @Test
    void titleWordsThatAreAlsoReleaseWordsAreKept() {
        assertEquals("The English Patient 1996", MediaDetection.stripReleaseInfo("The.English.Patient.1996.1080p.BluRay.mkv", false));
        assertEquals("Uncut Gems 2019", MediaDetection.stripReleaseInfo("Uncut.Gems.2019.UNCUT.WEB-DL.mkv", false));
        assertEquals("A Complete Unknown 2024", MediaDetection.stripReleaseInfo("A.Complete.Unknown.2024.COMPLETE.mkv", false));
        assertEquals("Charlotte's Web 2006", MediaDetection.stripReleaseInfo("Charlotte's.Web.2006.WEB.mkv", false));
        // site words go wherever they are
        assertEquals("Movie 2010", MediaDetection.stripReleaseInfo("www.Torrent.com.Movie.2010.mkv", false));
        assertEquals("The Killers", MediaDetection.detectMovie(new File("The.Killers.1946.mkv")).name());
    }

    @Test
    void yearRightAfterAYearIsTheReleaseYear() {
        assertEquals("Blade Runner 2049 (2017)", MediaDetection.detectMovie(new File("Blade.Runner.2049.2017.1080p.mkv")).toString());
        assertEquals("1917 (2019)", MediaDetection.detectMovie(new File("1917.2019.1080p.mkv")).toString());
        assertEquals(2017, MediaDetection.detectYear("Blade.Runner.2049.2017.1080p"));
        assertEquals(0, MediaDetection.detectYear("Movie.1080p"));
    }

    @Test
    void genericAndSeasonFolders() {
        for (String generic : new String[] { "Movies", "Películas", "TV Shows", "Downloads", "Season 2", "Specials", "CD1", "Disc 2", "Nueva carpeta" }) {
            assertTrue(MediaDetection.isGenericFolder(generic), generic);
        }
        for (String named : new String[] { "The Office", "Mediaeval Times", "Seasons", "TVF Pitchers" }) {
            assertFalse(MediaDetection.isGenericFolder(named), named);
        }
        assertEquals(2, MediaDetection.folderSeason(new File("D:/TV/Show/Season 2 (2019)/x.mkv")));
        assertNull(MediaDetection.folderSeason(new File("D:/TV/Show/S04E01 Pilot/x.mkv")));
    }

    @Test
    void parentSeasonCompletesBareNumbers() {
        assertEquals("S02E05 05 - Title.mkv", MediaDetection.nameWithParentSeason(new File("D:/Show/Season 2/05 - Title.mkv")));
        assertEquals("S01E03 Capitulo 3.mkv", MediaDetection.nameWithParentSeason(new File("D:/Show/Temporada 1/Capitulo 3.mkv")));
        assertEquals("Show.S03E01.mkv", MediaDetection.nameWithParentSeason(new File("D:/Show/Season 2/Show.S03E01.mkv")));
        assertEquals("05 - Title.mkv", MediaDetection.nameWithParentSeason(new File("D:/Show/05 - Title.mkv")));
    }

    @Test
    void groupsBySeriesInOrderOfAppearance() {
        Map<String, List<File>> groups = MediaDetection.groupBySeries(List.of(new File("Dark.S01E01.mkv"), new File("The.Office.S01E01.mkv"),
                new File("dark s01e02.mkv"), new File("D:/Downloads/S01E05.mkv")));
        assertEquals(List.of("Dark", "The Office", ""), List.copyOf(groups.keySet()));
        assertEquals(2, groups.get("Dark").size());
    }

    @Test
    void trailingGroupsAndSiteNamesAreNotPartOfTheName() {
        assertEquals("Game of Thrones S08E06 The Iron Throne",
                MediaDetection.stripReleaseInfo("Game.of.Thrones.S08E06.The.Iron.Throne.1080p.AMZN.WEB-DL.DDP5.1.H.264-GoT.mkv", false));
        assertEquals("The Shawshank Redemption 1994", MediaDetection.stripReleaseInfo("The.Shawshank.Redemption.1994.1080p.BluRay.x264-AMIABLE.mkv", false));
        assertEquals("Pelicula Titulo 2018", MediaDetection.stripReleaseInfo("www.DivxTotal.com - Pelicula Titulo 2018.avi", false));
        assertEquals("Parasite 2019", MediaDetection.stripReleaseInfo("Parasite.2019.KOREAN.1080p.BluRay.mkv", false));
        // a dash inside the title stays
        assertEquals("Spider-Man", MediaDetection.stripReleaseInfo("Spider-Man.mkv", false));
        assertEquals("9-1-1 S07E01", MediaDetection.stripReleaseInfo("9-1-1.S07E01.1080p.mkv", false));
    }

    @Test
    void compactDatesAndSpanishSeasonCodesEndTheSeriesName() {
        assertEquals("The Late Show", MediaDetection.detectSeriesName(new File("The.Late.Show.20240315.mkv")));
        assertEquals("La Casa de Papel", MediaDetection.detectSeriesName(new File("La.Casa.de.Papel.T1.Cap.05.mkv")));
    }

    @Test
    void discFoldersAreSkippedForMovies() {
        assertEquals("Matrix (1999)", MediaDetection.detectMovie(new File("D:/Peliculas/Matrix (1999)/CD1/matrix-cd1.avi")).toString());
    }

    @Test
    void movieQueryText() {
        assertEquals("Name (1999)", new MediaDetection.MovieQuery("Name", 1999).toString());
        assertEquals("Name", new MediaDetection.MovieQuery("Name", 0).toString());
    }

    @Test
    void hostileNamesAreFast() {
        String noise = "Movie " + "-.-_ (".repeat(3000) + "1999" + " .-".repeat(3000);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            MediaDetection.stripReleaseInfo(noise, true);
            MediaDetection.detectSeriesName(new File(noise));
            MediaDetection.detectMovie(new File(noise));
        });
    }
}
