package com.mediarenamer.rename.match;

import com.mediarenamer.media.MediaTypes;
import com.mediarenamer.similarity.Match;
import com.mediarenamer.web.AudioTrack;
import com.mediarenamer.web.MusicLookupService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Music mode: identifies each audio file with AcoustID or its ID3 tags. */
public class MusicMatcher implements AutoCompleteMatcher {

    private final MusicLookupService service;

    public MusicMatcher(MusicLookupService service) {
        this.service = service;
    }

    @Override
    public List<Match<Object, File>> match(List<File> files, Options o) throws Exception {
        List<File> audio = files.stream().filter(MediaTypes::isAudio).toList();
        Map<File, AudioTrack> tracks = service.lookup(audio.isEmpty() ? files : audio);
        List<Match<Object, File>> result = new ArrayList<>();
        for (File f : files) {
            AudioTrack t = tracks.get(f);
            if (t != null) {
                result.add(new Match<>(t, f));
            }
        }
        return result;
    }
}
