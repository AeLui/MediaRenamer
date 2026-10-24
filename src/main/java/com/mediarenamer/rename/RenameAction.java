package com.mediarenamer.rename;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Rename action: MOVE COPY KEEPLINK SYMLINK TEST.
 * HARDLINK, CLONE and DUPLICATE were removed in 2.1: saved values and the CLI names map to COPY.
 */
public enum RenameAction {

    /** Renames/moves the file. */
    MOVE("Rename"),
    /** Copies to the target and leaves the original. */
    COPY("Copy"),
    /** Moves and leaves a symbolic link at the original location (useful for seeding). */
    KEEPLINK("Keeplink"),
    /** Creates a symbolic link at the target. */
    SYMLINK("Symlink"),
    /** Does not touch the disk: only shows what would be done. */
    TEST("Test");

    private final String displayName;

    RenameAction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static RenameAction forName(String name) {
        if (name != null) {
            if (name.equalsIgnoreCase("hardlink") || name.equalsIgnoreCase("clone") || name.equalsIgnoreCase("duplicate")) {
                // removed actions: copying keeps the original file where it was, like they did
                return COPY;
            }
            for (RenameAction a : values()) {
                if (a.name().equalsIgnoreCase(name) || a.displayName.equalsIgnoreCase(name)) {
                    return a;
                }
            }
        }
        return MOVE;
    }

    /** Runs the action and returns the resulting file. Creates the target folders. */
    public File rename(File from, File to) throws IOException {
        if (this == TEST) {
            return to;
        }
        Path src = from.toPath();
        Path dst = to.toPath();
        if (dst.getParent() != null) {
            Files.createDirectories(dst.getParent());
        }
        switch (this) {
            case MOVE -> move(src, dst);
            case COPY -> Files.copy(src, dst, StandardCopyOption.COPY_ATTRIBUTES);
            case KEEPLINK -> {
                move(src, dst);
                Files.createSymbolicLink(src, dst.toAbsolutePath());
            }
            case SYMLINK -> Files.createSymbolicLink(dst, src.toAbsolutePath());
            default -> throw new IllegalStateException(name());
        }
        return to;
    }

    /** Move: atomic rename if possible; across volumes, copy + delete. Supports changing only the case. */
    private static void move(Path src, Path dst) throws IOException {
        boolean caseOnly = src.toAbsolutePath().toString().equalsIgnoreCase(dst.toAbsolutePath().toString());
        if (caseOnly) {
            Path tmp = src.resolveSibling(src.getFileName() + ".renaming");
            Files.move(src, tmp);
            Files.move(tmp, dst);
            return;
        }
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(src, dst);
        }
    }
}
