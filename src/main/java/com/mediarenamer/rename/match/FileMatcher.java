package com.mediarenamer.rename.match;

import com.mediarenamer.similarity.Match;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * File mode: each file is matched
 * with itself, to rename with file and MediaInfo variables ({fn}, {vf}, {crc32}...).
 */
public class FileMatcher implements AutoCompleteMatcher {

    @Override
    public List<Match<Object, File>> match(List<File> files, Options o) {
        List<Match<Object, File>> result = new ArrayList<>();
        for (File f : files) {
            result.add(new Match<>(f, f));
        }
        return result;
    }
}
