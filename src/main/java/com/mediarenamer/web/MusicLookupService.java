package com.mediarenamer.web;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/** Music identification (AcoustID by acoustic fingerprint, ID3 by tags). */
public interface MusicLookupService extends Datasource {

    Map<File, AudioTrack> lookup(Collection<File> files) throws Exception;
}
