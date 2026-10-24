package com.mediarenamer.format;

import com.mediarenamer.app.Logging;
import com.mediarenamer.app.Settings;
import com.mediarenamer.media.MediaCharacteristics;
import com.mediarenamer.util.FileUtilities;
import com.mediarenamer.media.MediaTypes;
import com.mediarenamer.media.ReleaseInfo;
import com.mediarenamer.web.AnimeLists;
import com.mediarenamer.web.AudioTrack;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.EpisodeFormat;
import com.mediarenamer.web.Movie;
import com.mediarenamer.web.MovieInfo;
import com.mediarenamer.web.MovieLookupService;
import com.mediarenamer.web.MultiEpisode;
import com.mediarenamer.web.SeriesInfo;
import com.mediarenamer.web.SimpleDate;
import com.mediarenamer.web.WebServices;
import com.mediarenamer.web.XEM;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

/**
 * Variables available in formats ({n}, {s00e00}, {vf}...).
 *
 * Each format variable is a getter annotated with @Define (see docs/FORMAT.md for the list and their status).
 *
 * @param infoObject Episode, MultiEpisode, Movie, AudioTrack or File (file mode)
 * @param mediaFile  original file to be renamed
 * @param context    all current matches (for {i}, {di}, {dc})
 */
public class MediaBindingBean {

    private final Object infoObject;
    private final File mediaFile;
    private final Map<File, ?> context;

    private MediaCharacteristics media;
    private MovieInfo movieInfo;
    private boolean movieInfoLoaded;

    public MediaBindingBean(Object infoObject, File mediaFile, Map<File, ?> context) {
        this.infoObject = infoObject;
        this.mediaFile = mediaFile;
        this.context = context == null ? Map.of() : context;
    }

    public MediaBindingBean(Object infoObject, File mediaFile) {
        this(infoObject, mediaFile, null);
    }

    // ------------------------------------------------------------------ objects

    @Define("object")
    public Object getInfoObject() {
        return infoObject;
    }

    @Define("file")
    public File getFileObject() {
        return mediaFile;
    }

    @Define("episode")
    public Episode getEpisode() {
        return infoObject instanceof Episode e ? e : null;
    }

    @Define("episodes")
    public List<Episode> getEpisodes() {
        if (infoObject instanceof MultiEpisode m) {
            return m.getEpisodes();
        }
        return infoObject instanceof Episode e ? List.of(e) : null;
    }

    @Define("movie")
    public Movie getMovie() {
        return infoObject instanceof Movie m ? m : null;
    }

    @Define("music")
    public AudioTrack getMusic() {
        return infoObject instanceof AudioTrack t ? t : null;
    }

    @Define("series")
    public SeriesInfo getSeriesInfo() {
        Episode e = getEpisode();
        return e == null ? null : e.getSeriesInfo();
    }

    @Define("type")
    public String getInfoObjectType() {
        return infoObject == null ? null : infoObject.getClass().getSimpleName();
    }

    // ------------------------------------------------------------------ name, year, title

    /** {n}: series, movie or artist. */
    @Define("n")
    public String getName() {
        if (infoObject instanceof Episode e) {
            return e.getSeriesName();
        }
        if (infoObject instanceof Movie m) {
            return m.getName();
        }
        if (infoObject instanceof AudioTrack t) {
            return t.getArtist() != null ? t.getArtist() : t.getAlbumArtist();
        }
        if (infoObject instanceof File f) {
            return nameWithoutExtension(f);
        }
        return null;
    }

    @Define("y")
    public Integer getYear() {
        if (infoObject instanceof Movie m) {
            return m.getYear() > 0 ? m.getYear() : null;
        }
        if (infoObject instanceof Episode e) {
            SeriesInfo s = e.getSeriesInfo();
            if (s != null && s.getStartDate() != null) {
                return s.getStartDate().getYear();
            }
            return e.getAirdate() == null ? null : e.getAirdate().getYear();
        }
        if (infoObject instanceof AudioTrack t) {
            return t.getAlbumReleaseDate() == null ? null : t.getAlbumReleaseDate().getYear();
        }
        return null;
    }

    /** {ny}: "Name (Year)" unless the name already ends with the year. */
    @Define("ny")
    public String getNameWithYear() {
        String n = getName();
        Integer y = getYear();
        if (y == null || n.endsWith("(" + y + ")") || n.endsWith(" " + y)) {
            return n;
        }
        return n + " (" + y + ")";
    }

    @Define("t")
    public String getTitle() {
        if (infoObject instanceof MultiEpisode m) {
            // multi-episode titles: "Title (1) & Title (2)" -> "Title (1) & Title (2)" without duplicates
            return m.getEpisodes().stream().map(Episode::getTitle).filter(Objects::nonNull).distinct().collect(Collectors.joining(" & "));
        }
        if (infoObject instanceof Episode e) {
            return e.getTitle();
        }
        if (infoObject instanceof AudioTrack t) {
            return t.getTitle();
        }
        if (infoObject instanceof Movie m) {
            return m.getName();
        }
        return null;
    }

    @Define("primaryTitle")
    public String getPrimaryTitle() {
        if (infoObject instanceof Movie m) {
            MovieInfo info = getMovieInfo();
            return info != null && info.originalName != null ? info.originalName : m.getAliasNames().stream().findFirst().orElse(m.getName());
        }
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getAliasNames().stream().findFirst().orElse(s.getName());
    }

    @Define("alias")
    public List<String> getAliasNames() {
        if (infoObject instanceof Movie m) {
            return m.getAliasNames();
        }
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getAliasNames();
    }

    // ------------------------------------------------------------------ episodes

    @Define("s")
    public Integer getSeasonNumber() {
        Episode e = getEpisode();
        if (e == null) {
            return null;
        }
        return e.isSpecial() ? Integer.valueOf(0) : e.getSeason();
    }

    @Define("sn")
    public String getSeasonName() {
        Integer s = getSeasonNumber();
        return s == null ? null : s == 0 ? "Specials" : "Season " + s;
    }

    @Define("e")
    public Integer getEpisodeNumber() {
        Episode e = getEpisode();
        if (e == null) {
            return null;
        }
        return e.isSpecial() ? e.getSpecial() : e.getEpisode();
    }

    @Define("es")
    public List<Integer> getEpisodeNumbers() {
        List<Episode> l = getEpisodes();
        return l == null ? null : l.stream().map(e -> e.isSpecial() ? e.getSpecial() : e.getEpisode()).filter(Objects::nonNull).toList();
    }

    @Define("s00")
    public String getS00() {
        Integer s = getSeasonNumber();
        return s == null ? null : String.format("%02d", s);
    }

    @Define("e00")
    public String getE00() {
        List<Integer> es = getEpisodeNumbers();
        if (es == null || es.isEmpty()) {
            return null;
        }
        return es.stream().map(n -> String.format("%02d", n)).collect(Collectors.joining("-"));
    }

    @Define("sxe")
    public String getSxE() {
        Episode e = getEpisode();
        return e == null ? null : EpisodeFormat.SxE.format(e);
    }

    @Define("s00e00")
    public String getS00E00() {
        Episode e = getEpisode();
        return e == null ? null : EpisodeFormat.S00E00.format(e);
    }

    @Define("absolute")
    public Integer getAbsoluteEpisodeNumber() {
        Episode e = getEpisode();
        return e == null ? null : e.getAbsolute();
    }

    @Define("special")
    public Integer getSpecialNumber() {
        Episode e = getEpisode();
        return e == null ? null : e.getSpecial();
    }

    @Define("regular")
    public Boolean isRegular() {
        Episode e = getEpisode();
        return e == null ? null : !e.isSpecial();
    }

    /** {d}: air date of the episode or release date of the movie. */
    @Define({ "d", "airdate" })
    public SimpleDate getReleaseDate() {
        if (infoObject instanceof Episode e) {
            return e.getAirdate();
        }
        if (infoObject instanceof Movie) {
            MovieInfo i = getMovieInfo();
            return i == null ? null : i.released;
        }
        if (infoObject instanceof AudioTrack t) {
            return t.getAlbumReleaseDate();
        }
        return null;
    }

    @Define("startdate")
    public SimpleDate getStartDate() {
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getStartDate();
    }

    @Define("age")
    public Long getAge() {
        SimpleDate d = getReleaseDate();
        return d == null ? null : java.time.temporal.ChronoUnit.DAYS.between(d.toLocalDate(), LocalDate.now());
    }

    @Define("anime")
    public Boolean isAnime() {
        SeriesInfo s = getSeriesInfo();
        if (s != null && "AniDB".equals(s.getDatabase())) {
            return true;
        }
        List<String> genres = getGenres();
        return genres != null && genres.stream().anyMatch(g -> g.equalsIgnoreCase("Anime") || g.equalsIgnoreCase("Animation") && "ja".equals(getOriginalLanguage()));
    }

    // ------------------------------------------------------------------ ids

    @Define("id")
    public Object getId() {
        if (infoObject instanceof Movie m) {
            return m.getTmdbId() > 0 ? m.getTmdbId() : String.format("tt%07d", m.getImdbId());
        }
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getId();
    }

    @Define("tmdbid")
    public String getTmdbId() {
        if (infoObject instanceof Movie m) {
            return m.getTmdbId() > 0 ? String.valueOf(m.getTmdbId()) : null;
        }
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getExternalIds().get("tmdb");
    }

    @Define("tvdbid")
    public String getTvdbId() {
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getExternalIds().get("tvdb");
    }

    @Define("imdbid")
    public String getImdbId() {
        if (infoObject instanceof Movie m) {
            if (m.getImdbId() > 0) {
                return String.format("tt%07d", m.getImdbId());
            }
            MovieInfo i = getMovieInfo();
            return i == null ? null : i.externalIds.get("imdb");
        }
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getExternalIds().get("imdb");
    }

    // ------------------------------------------------------------------ extended metadata

    /** {info}: MovieInfo (movies) or SeriesInfo (series). */
    @Define("info")
    public Object getInfo() {
        return infoObject instanceof Movie ? getMovieInfo() : getSeriesInfo();
    }

    @Define("genres")
    public List<String> getGenres() {
        if (infoObject instanceof Movie) {
            MovieInfo i = getMovieInfo();
            return i == null ? null : i.genres;
        }
        if (infoObject instanceof AudioTrack t) {
            return t.getGenre() == null ? null : List.of(t.getGenre());
        }
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getGenres();
    }

    @Define("genre")
    public String getPrimaryGenre() {
        List<String> g = getGenres();
        return g == null || g.isEmpty() ? null : g.get(0);
    }

    @Define("rating")
    public Double getRating() {
        if (infoObject instanceof Movie) {
            MovieInfo i = getMovieInfo();
            return i == null ? null : i.rating;
        }
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getRating();
    }

    @Define("votes")
    public Integer getVotes() {
        if (infoObject instanceof Movie) {
            MovieInfo i = getMovieInfo();
            return i == null ? null : i.votes;
        }
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getRatingCount();
    }

    @Define("certification")
    public String getCertification() {
        if (infoObject instanceof Movie) {
            MovieInfo i = getMovieInfo();
            return i == null ? null : i.certification;
        }
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getCertification();
    }

    @Define("collection")
    public String getCollection() {
        MovieInfo i = infoObject instanceof Movie ? getMovieInfo() : null;
        return i == null ? null : i.collection;
    }

    @Define("director")
    public String getDirector() {
        MovieInfo i = infoObject instanceof Movie ? getMovieInfo() : null;
        return i == null ? null : i.getDirector();
    }

    @Define("actors")
    public List<String> getActors() {
        MovieInfo i = infoObject instanceof Movie ? getMovieInfo() : null;
        return i == null ? null : i.actors;
    }

    @Define("runtime")
    public Integer getRuntime() {
        if (infoObject instanceof Movie) {
            MovieInfo i = getMovieInfo();
            return i == null ? null : i.runtime;
        }
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getRuntime();
    }

    @Define("country")
    public String getCountry() {
        if (infoObject instanceof Movie) {
            MovieInfo i = getMovieInfo();
            return i == null || i.countries.isEmpty() ? null : i.countries.get(0);
        }
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getCountry();
    }

    @Define("language")
    public String getOriginalLanguage() {
        if (infoObject instanceof Movie) {
            MovieInfo i = getMovieInfo();
            return i == null ? null : i.originalLanguage;
        }
        SeriesInfo s = getSeriesInfo();
        return s == null ? null : s.getOriginalLanguage();
    }

    @Define("languages")
    public List<String> getSpokenLanguages() {
        MovieInfo i = infoObject instanceof Movie ? getMovieInfo() : null;
        return i == null ? null : i.spokenLanguages;
    }

    @Define("az")
    public String getSortInitial() {
        String n = getName();
        return n == null ? null : FormatMethods.sortInitial(n);
    }

    @Define("decade")
    public String getDecade() {
        Integer y = getYear();
        return y == null ? null : (y / 10 * 10) + "s";
    }

    /** {omdb}: OMDb data for the movie (requires apikey.omdb). */
    @Define("omdb")
    public MovieInfo getOmdb() throws Exception {
        Movie m = getMovie();
        if (m == null) {
            return null;
        }
        int imdb = m.getImdbId();
        if (imdb <= 0 && getImdbId() != null) {
            imdb = Integer.parseInt(getImdbId().replaceAll("\\D", ""));
        }
        return imdb <= 0 ? null : WebServices.OMDb.getMovieInfo(new Movie(m.getName(), null, m.getYear(), imdb, 0, null), Settings.get().getLocale());
    }

    private MovieInfo getMovieInfo() {
        if (!movieInfoLoaded) {
            movieInfoLoaded = true;
            Movie m = getMovie();
            if (m != null) {
                try {
                    MovieLookupService db = m.getTmdbId() > 0 ? WebServices.TheMovieDB : WebServices.OMDb;
                    movieInfo = db.getMovieInfo(m, m.getLanguage() != null ? m.getLanguage() : Settings.get().getLocale());
                } catch (Exception e) {
                    Logging.debug("MovieInfo: " + e.getMessage());
                }
            }
        }
        return movieInfo;
    }

    // ------------------------------------------------------------------ music

    @Define("artist")
    public String getArtist() {
        return getMusic() == null ? null : getMusic().getArtist();
    }

    @Define("albumArtist")
    public String getAlbumArtist() {
        return getMusic() == null ? null : getMusic().getAlbumArtist();
    }

    @Define("album")
    public String getAlbum() {
        return getMusic() == null ? null : getMusic().getAlbum();
    }

    @Define("pi")
    public Integer getPartIndex() {
        return getMusic() == null ? null : getMusic().getTrackIndex();
    }

    @Define("pn")
    public Integer getPartCount() {
        return getMusic() == null ? null : getMusic().getTrackCount();
    }

    @Define("medium")
    public Integer getMedium() {
        return getMusic() == null ? null : getMusic().getMediumIndex();
    }

    // ------------------------------------------------------------------ file

    @Define("f")
    public File getMediaFile() {
        return mediaFile;
    }

    @Define("folder")
    public File getMediaParentFolder() {
        return mediaFile == null ? null : mediaFile.getParentFile();
    }

    /** {fn}: original name without extension. */
    @Define("fn")
    public String getFileName() {
        return mediaFile == null ? null : nameWithoutExtension(mediaFile);
    }

    @Define("ext")
    public String getExtension() {
        return mediaFile == null ? null : MediaTypes.extension(mediaFile);
    }

    /** {original}: original name (without extension) before the first rename recorded in the history. */
    @Define("original")
    public String getOriginalFileName() {
        if (mediaFile == null) {
            return null;
        }
        String original = com.mediarenamer.rename.History.get().originalName(mediaFile);
        return nameWithoutExtension(new File(original != null ? original : mediaFile.getName()));
    }

    @Define("drive")
    public File getMountPoint() {
        if (mediaFile == null) {
            return null;
        }
        File root = mediaFile.getAbsoluteFile();
        while (root.getParentFile() != null) {
            root = root.getParentFile();
        }
        return root;
    }

    @Define("home")
    public File getUserHome() {
        return new File(System.getProperty("user.home"));
    }

    @Define("today")
    public SimpleDate getToday() {
        return SimpleDate.of(LocalDate.now());
    }

    @Define("bytes")
    public Long getFileSize() {
        return mediaFile == null || !mediaFile.isFile() ? null : mediaFile.length();
    }

    @Define("megabytes")
    public Long getFileSizeMB() {
        Long b = getFileSize();
        return b == null ? null : Math.round(b / 1e6);
    }

    @Define("gigabytes")
    public String getFileSizeGB() {
        Long b = getFileSize();
        return b == null ? null : String.format(Locale.ROOT, "%.1f", b / 1e9);
    }

    /** {crc32}: CRC from the name ([ABCD1234]) or computed over the content. */
    @Define("crc32")
    public String getCRC32() throws IOException {
        if (mediaFile == null) {
            return null;
        }
        Matcher m = Pattern.compile("[\\(\\[](\\p{XDigit}{8})[\\]\\)]").matcher(mediaFile.getName());
        if (m.find()) {
            return m.group(1).toUpperCase();
        }
        if (!mediaFile.isFile()) {
            return null;
        }
        CRC32 crc = new CRC32();
        byte[] buf = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(mediaFile.toPath())) {
            for (int n; (n = in.read(buf)) > 0;) {
                crc.update(buf, 0, n);
            }
        }
        return String.format("%08X", crc.getValue());
    }

    /** Index of the item in the current list (1..n). */
    @Define("i")
    public Integer getModelIndex() {
        int i = 1;
        for (File f : context.keySet()) {
            if (f.equals(mediaFile)) {
                return i;
            }
            i++;
        }
        return null;
    }

    /** Duplicate index: 1, 2... among files matched to the same object. */
    @Define("di")
    public Integer getDuplicateIndex() {
        int i = 0;
        for (Map.Entry<File, ?> e : context.entrySet()) {
            if (Objects.equals(e.getValue(), infoObject)) {
                i++;
                if (e.getKey().equals(mediaFile)) {
                    return i;
                }
            }
        }
        return null;
    }

    @Define("dc")
    public Integer getDuplicateCount() {
        long n = context.values().stream().filter(v -> Objects.equals(v, infoObject)).count();
        return n == 0 ? null : (int) n;
    }

    // ------------------------------------------------------------------ release info from the original name

    @Define({ "vs", "source" })
    public String getVideoSource() {
        return mediaFile == null ? null : ReleaseInfo.getVideoSource(getOriginalFileName());
    }

    @Define({ "tags", "edition" })
    public String getVideoTags() {
        return mediaFile == null ? null : ReleaseInfo.getVideoTags(getOriginalFileName());
    }

    @Define("group")
    public String getReleaseGroup() {
        return mediaFile == null ? null : ReleaseInfo.getReleaseGroup(getOriginalFileName());
    }

    @Define("s3d")
    public String getStereoscopic3D() {
        if (mediaFile == null) {
            return null;
        }
        Matcher m = ReleaseInfo.VIDEO_S3D.matcher(getOriginalFileName());
        return m.find() ? m.group() : null;
    }

    /** {lang}: subtitle language given in the name ("movie.spa.srt" -> "spa"). */
    @Define("lang")
    public String getSubtitleLanguage() {
        if (mediaFile == null || !MediaTypes.isSubtitle(mediaFile)) {
            return null;
        }
        Matcher m = Pattern.compile("[._ -]([a-z]{2,3}(?:-[A-Z]{2})?)$", Pattern.CASE_INSENSITIVE).matcher(nameWithoutExtension(mediaFile));
        return m.find() ? m.group(1) : null;
    }

    @Define("subt")
    public String getSubtitleTags() {
        String lang = getSubtitleLanguage();
        if (lang == null) {
            return null;
        }
        Matcher m = ReleaseInfo.SUBTITLE_TAGS.matcher(nameWithoutExtension(mediaFile));
        return "." + lang + (m.find() ? "." + m.group() : "");
    }

    // ------------------------------------------------------------------ MediaInfo

    private MediaCharacteristics media() {
        if (media == null) {
            if (mediaFile == null || !mediaFile.isFile()) {
                return null;
            }
            // subtitles and nfo: the properties of the video they accompany are used
            File target = mediaFile;
            if (MediaTypes.isCompanion(mediaFile)) {
                File video = findCompanionVideo(mediaFile);
                if (video != null) {
                    target = video;
                }
            }
            media = new MediaCharacteristics(target);
        }
        return media.isEmpty() ? null : media;
    }

    private static File findCompanionVideo(File f) {
        File dir = f.getParentFile();
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return null;
        }
        String base = nameWithoutExtension(f).toLowerCase();
        for (File v : files) {
            if (MediaTypes.isVideo(v) && base.startsWith(nameWithoutExtension(v).toLowerCase())) {
                return v;
            }
        }
        return null;
    }

    @Define("vf")
    public String getVideoFormat() {
        return media() == null ? null : media().videoFormat();
    }

    @Define("vk")
    public String getVideoFormatK() {
        return media() == null ? null : media().videoFormatK();
    }

    @Define("hd")
    public String getVideoDefinitionCategory() {
        return media() == null ? null : media().definition();
    }

    @Define("vc")
    public String getVideoCodec() {
        return media() == null ? null : media().videoCodec();
    }

    @Define("ac")
    public String getAudioCodec() {
        return media() == null ? null : media().audioCodec();
    }

    @Define("cf")
    public String getContainerFormat() {
        return media() == null ? null : media().containerFormat();
    }

    @Define("channels")
    public String getAudioChannels() {
        return media() == null ? null : media().channels();
    }

    @Define("af")
    public String getAudioChannelFormat() {
        return media() == null ? null : media().audioChannelFormat();
    }

    @Define("resolution")
    public String getVideoResolution() {
        return media() == null || media().width() == null ? null : media().width() + "x" + media().height();
    }

    @Define("width")
    public Integer getWidth() {
        return media() == null ? null : media().width();
    }

    @Define("height")
    public Integer getHeight() {
        return media() == null ? null : media().height();
    }

    @Define("hdr")
    public String getHighDynamicRange() {
        return media() == null ? null : media().hdr();
    }

    @Define("dovi")
    public Boolean getDolbyVision() {
        return media() == null ? null : media().dolbyVision() ? Boolean.TRUE : null;
    }

    @Define("bitdepth")
    public Integer getBitDepth() {
        return media() == null ? null : media().bitDepth();
    }

    @Define("ar")
    public String getAspectRatio() {
        return media() == null ? null : media().aspectRatio();
    }

    @Define("ws")
    public String getWidescreen() {
        Double ar = media() == null ? null : media().aspectRatioValue();
        return ar == null ? null : ar > 1.37 ? "WS" : null;
    }

    @Define("fps")
    public String getFrameRate() {
        return media() == null ? null : media().frameRate();
    }

    @Define("khz")
    public String getSamplingRate() {
        Integer hz = media() == null ? null : media().samplingRate();
        return hz == null ? null : (hz % 1000 == 0 ? String.valueOf(hz / 1000) : String.format(Locale.ROOT, "%.1f", hz / 1000.0)) + "kHz";
    }

    @Define("duration")
    public Duration getDuration() {
        Long ms = media() == null ? null : media().durationMillis();
        return ms == null ? null : Duration.ofMillis(ms);
    }

    @Define("seconds")
    public Long getSeconds() {
        Duration d = getDuration();
        return d == null ? null : d.toSeconds();
    }

    @Define("minutes")
    public Long getMinutes() {
        Duration d = getDuration();
        return d == null ? null : d.toMinutes();
    }

    @Define("hours")
    public String getHours() {
        Duration d = getDuration();
        return d == null ? null : String.format("%d:%02d", d.toHours(), d.toMinutesPart());
    }

    @Define("bitrate")
    public Long getOverallBitRate() {
        return media() == null ? null : media().overallBitRate();
    }

    @Define("vbr")
    public String getVideoBitRate() {
        Long b = media() == null ? null : media().videoBitRate();
        return b == null ? null : Math.round(b / 1000.0) + " kbps";
    }

    @Define("abr")
    public String getAudioBitRate() {
        Long b = media() == null ? null : media().audioBitRate();
        return b == null ? null : Math.round(b / 1000.0) + " kbps";
    }

    @Define("kbps")
    public String getKiloBytesPerSecond() {
        Long b = getOverallBitRate();
        return b == null ? null : Math.round(b / 1000.0) + " kbps";
    }

    @Define("mbps")
    public String getMegaBytesPerSecond() {
        Long b = getOverallBitRate();
        return b == null ? null : String.format(Locale.ROOT, "%.1f Mbps", b / 1e6);
    }

    @Define("mediaTitle")
    public String getMediaTitle() {
        return media() == null ? null : media().mediaTitle();
    }

    @Define("audioLanguages")
    public List<String> getAudioLanguageList() {
        return media() == null ? null : media().languages("Audio");
    }

    @Define("textLanguages")
    public List<String> getTextLanguageList() {
        return media() == null ? null : media().languages("Text");
    }

    @Define("media")
    public Map<String, String> getGeneralProperties() {
        return media() == null ? null : media().general();
    }

    @Define("video")
    public List<Map<String, String>> getVideoPropertiesList() {
        return media() == null ? null : media().streams("Video");
    }

    @Define("audio")
    public List<Map<String, String>> getAudioPropertiesList() {
        return media() == null ? null : media().streams("Audio");
    }

    @Define("text")
    public List<Map<String, String>> getTextPropertiesList() {
        return media() == null ? null : media().streams("Text");
    }

    @Define("menu")
    public List<Map<String, String>> getMenuPropertiesList() {
        return media() == null ? null : media().streams("Menu");
    }

    // ------------------------------------------------------------------ naming standards

    private NamingStandard.Path standard(NamingStandard s) {
        String part = null;
        if (infoObject instanceof Movie && mediaFile != null) {
            Matcher m = Pattern.compile("(?i)(?<!\\p{Alnum})(?:CD|Disc|Part|Pt)[\\P{Alnum}]?(\\d)(?!\\p{Alnum})").matcher(mediaFile.getName());
            if (m.find()) {
                part = " - CD" + m.group(1);
            }
        }
        return s.getPath(infoObject, part);
    }

    @Define("plex")
    public NamingStandard.Path getPlexStandardPath() {
        return standard(NamingStandard.Plex);
    }

    @Define("kodi")
    public NamingStandard.Path getKodiStandardPath() {
        return standard(NamingStandard.Kodi);
    }

    @Define("emby")
    public NamingStandard.Path getEmbyStandardPath() {
        return standard(NamingStandard.Emby);
    }

    @Define("jellyfin")
    public NamingStandard.Path getJellyfinStandardPath() {
        return standard(NamingStandard.Jellyfin);
    }

    // ------------------------------------------------------------------ anime mappings

    /** {AnimeList.TheTVDB}: AniDB episode converted to TheTVDB numbering. */
    @Define("AnimeList")
    public Map<String, Object> getAnimeLists() throws Exception {
        Episode e = getEpisode();
        if (e == null) {
            return null;
        }
        Episode tvdb = AnimeLists.toTheTVDB(e).orElse(null);
        return tvdb == null ? null : Map.of("TheTVDB", tvdb, "tvdb", tvdb);
    }

    /** {XEM.scene}: TheTVDB episode converted to "scene" numbering with TheXEM. */
    @Define("XEM")
    public Map<String, Object> getXrossEntityMapper() throws Exception {
        Episode e = getEpisode();
        String tvdb = getTvdbId();
        if (e == null || tvdb == null) {
            return null;
        }
        Episode scene = XEM.map(e, Integer.parseInt(tvdb), "tvdb", "scene").orElse(null);
        return scene == null ? null : Map.of("scene", scene, "Scene", scene);
    }

    @Define("self")
    public MediaBindingBean getSelf() {
        return this;
    }

    // ------------------------------------------------------------------ utilities

    /** List of all variables with their current value ("View Bindings" dialog). */
    public List<String[]> describe() {
        ExpressionBinding b = new ExpressionBinding(this, null);
        List<String[]> rows = new ArrayList<>();
        for (String name : b.getDefinedNames()) {
            if (name.equals("self") || name.equals("crc32") && mediaFile != null && mediaFile.length() > 50_000_000L) {
                continue;
            }
            Object v = b.getValueOrNull(name);
            rows.add(new String[] { name, v == null ? "" : String.valueOf(v) });
        }
        return rows;
    }

    private static String nameWithoutExtension(File f) {
        return FileUtilities.getNameWithoutExtension(f.getName());
    }
}
