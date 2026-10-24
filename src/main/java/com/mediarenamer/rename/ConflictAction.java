package com.mediarenamer.rename;

/**
 * What to do if the target already exists (--conflict option).
 * "Keep" refers to which of the two files stays at the target: if the new one wins, the
 * existing one is sent to the recycle bin; if the existing one wins, the new one is not renamed.
 */
public enum ConflictAction {
    /** Ask the user on each conflict, showing quality and size of both. Default option in the interface. */
    ASK("ask"),
    /** Do not rename that file (the existing one is kept). */
    SKIP("skip"),
    /** Always replace the existing one. */
    OVERRIDE("override"),
    /** Keep the higher quality one (resolution, HDR, bitrate...). CLI alias "auto". */
    BEST_QUALITY("best", "auto", "quality", "best_quality"),
    /** Keep the lower quality one. */
    LOWEST_QUALITY("worst", "lowest", "lowest_quality"),
    /** Keep the larger file. */
    LARGEST("largest", "larger", "bigger"),
    /** Keep the smaller file. */
    SMALLEST("smallest", "smaller", "lighter"),
    /** Keep both: the new one gets an index "Name (1).mkv". */
    INDEX("index"),
    /** Abort the whole process. */
    FAIL("fail");

    private final String[] aliases;

    ConflictAction(String... aliases) {
        this.aliases = aliases;
    }

    /** Name in the interface language ("Ask each time", "Skip (don't rename)"...). */
    public String getLabel() {
        return com.mediarenamer.app.I18n.tr("conflict." + name());
    }

    /** CLI name or alias ("auto" means BEST_QUALITY); SKIP by default. */
    public static ConflictAction forName(String s) {
        return forName(s, SKIP);
    }

    /** Same as forName(String), with a default value if none is given or it is not recognized. */
    public static ConflictAction forName(String s, ConflictAction defaultValue) {
        if (s != null) {
            String n = s.trim().toLowerCase().replace('-', '_');
            for (ConflictAction c : values()) {
                if (c.name().equalsIgnoreCase(n)) {
                    return c;
                }
                for (String a : c.aliases) {
                    if (a.equals(n)) {
                        return c;
                    }
                }
            }
        }
        return defaultValue;
    }

    @Override
    public String toString() {
        return getLabel();
    }
}
