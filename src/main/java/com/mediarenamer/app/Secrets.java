package com.mediarenamer.app;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encryption of the API keys stored in settings.properties.
 * <ul>
 * <li>Windows: DPAPI ({@code CryptProtectData}, user scope) via JNA → {@code dpapi:<base64>}.
 * Only that Windows user on that machine can decrypt it; copying the file to another machine does not work.</li>
 * <li>Other systems: AES-256-GCM with a random key in {@code secret.key} (permissions 600) next to the
 * preferences file → {@code aes:<base64(iv+data)>}.</li>
 * </ul>
 * Values without a prefix are considered legacy (plain text) and {@link Settings} encrypts them on load. DPAPI values
 * of FileRenamer (the application's name before 2.0) use another entropy: they are still decrypted and
 * {@link #reprotect(String, Path)} encrypts them again with the current one.
 */
public final class Secrets {

    static final String DPAPI = "dpapi:";
    static final String AES = "aes:";
    /** Additional DPAPI entropy: another application of the same user cannot decrypt it with the default call. */
    private static final byte[] ENTROPY = "MediaRenamer/apikeys/v1".getBytes(StandardCharsets.UTF_8);
    /** Entropy of the values written by FileRenamer (1.x). */
    static final byte[] LEGACY_ENTROPY = "FileRenamer/apikeys/v1".getBytes(StandardCharsets.UTF_8);
    private static final int CRYPTPROTECT_UI_FORBIDDEN = 0x1;

    private Secrets() {
    }

    public static boolean isProtected(String value) {
        return value != null && (value.startsWith(DPAPI) || value.startsWith(AES));
    }

    /** Encrypts a plain-text value. */
    public static String protect(String plain, Path keyFolder) {
        byte[] data = plain.getBytes(StandardCharsets.UTF_8);
        try {
            if (isWindows()) {
                return DPAPI + Base64.getEncoder().encodeToString(dpapi(data, true, ENTROPY));
            }
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            c.init(Cipher.ENCRYPT_MODE, aesKey(keyFolder), new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(data);
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return AES + Base64.getEncoder().encodeToString(out);
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException(I18n.tr("error.encrypt", e.getMessage()), e);
        }
    }

    /** Decrypts a protected value; values without a prefix are returned as is. */
    public static String unprotect(String value, Path keyFolder) {
        if (!isProtected(value)) {
            return value;
        }
        try {
            if (value.startsWith(DPAPI)) {
                byte[] in = Base64.getDecoder().decode(value.substring(DPAPI.length()));
                try {
                    return new String(dpapi(in, false, ENTROPY), StandardCharsets.UTF_8);
                } catch (IllegalStateException e) {
                    return new String(dpapi(in, false, LEGACY_ENTROPY), StandardCharsets.UTF_8);
                }
            }
            byte[] in = Base64.getDecoder().decode(value.substring(AES.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, aesKey(keyFolder), new GCMParameterSpec(128, in, 0, 12));
            return new String(c.doFinal(in, 12, in.length - 12), StandardCharsets.UTF_8);
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException(I18n.tr("error.decrypt", e.getMessage()), e);
        }
    }

    /**
     * The value encrypted again with the current DPAPI entropy if it was written by FileRenamer; null if it is already
     * current (or is not a DPAPI value) and nothing has to be saved.
     */
    public static String reprotect(String value, Path keyFolder) {
        if (value == null || !value.startsWith(DPAPI) || !isWindows()) {
            return null;
        }
        byte[] in = Base64.getDecoder().decode(value.substring(DPAPI.length()));
        try {
            dpapi(in, false, ENTROPY);
            return null;
        } catch (RuntimeException e) {
            // not current: it is decrypted below with the legacy entropy
        }
        try {
            return protect(new String(dpapi(in, false, LEGACY_ENTROPY), StandardCharsets.UTF_8), keyFolder);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    // ------------------------------------------------------------------ DPAPI (Crypt32.dll)

    /** Win32 DATA_BLOB. */
    @Structure.FieldOrder({ "cbData", "pbData" })
    public static class DataBlob extends Structure {
        public int cbData;
        public Pointer pbData;

        DataBlob() {
        }

        DataBlob(byte[] data) {
            Memory m = new Memory(Math.max(1, data.length));
            m.write(0, data, 0, data.length);
            cbData = data.length;
            pbData = m;
        }

        byte[] bytes() {
            return pbData == null ? new byte[0] : pbData.getByteArray(0, cbData);
        }
    }

    interface Crypt32 extends Library {
        boolean CryptProtectData(DataBlob in, WString descr, DataBlob entropy, Pointer reserved, Pointer prompt, int flags, DataBlob out);

        boolean CryptUnprotectData(DataBlob in, Pointer descr, DataBlob entropy, Pointer reserved, Pointer prompt, int flags, DataBlob out);
    }

    interface Kernel32 extends Library {
        Pointer LocalFree(Pointer p);

        int GetLastError();
    }

    /** Both libraries are loaded together on first use (lazy holder: no thread can see one without the other). */
    private static final class Win32 {
        static final Crypt32 CRYPT32 = Native.load("Crypt32", Crypt32.class);
        static final Kernel32 KERNEL32 = Native.load("Kernel32", Kernel32.class);
    }

    static byte[] dpapi(byte[] data, boolean protect, byte[] entropyBytes) {
        Crypt32 crypt32 = Win32.CRYPT32;
        Kernel32 kernel32 = Win32.KERNEL32;
        DataBlob in = new DataBlob(data);
        DataBlob entropy = new DataBlob(entropyBytes);
        DataBlob out = new DataBlob();
        boolean ok = protect
                ? crypt32.CryptProtectData(in, new WString(Settings.APP_NAME), entropy, null, null, CRYPTPROTECT_UI_FORBIDDEN, out)
                : crypt32.CryptUnprotectData(in, null, entropy, null, null, CRYPTPROTECT_UI_FORBIDDEN, out);
        if (!ok) {
            throw new IllegalStateException("DPAPI error " + kernel32.GetLastError());
        }
        try {
            out.read();
            return out.bytes();
        } finally {
            kernel32.LocalFree(out.pbData);
        }
    }

    // ------------------------------------------------------------------ AES (other systems)

    private static SecretKey aesKey(Path folder) throws IOException, GeneralSecurityException {
        Path keyFile = folder.resolve("secret.key");
        if (Files.isRegularFile(keyFile)) {
            return new SecretKeySpec(Base64.getDecoder().decode(Files.readString(keyFile).trim()), "AES");
        }
        KeyGenerator g = KeyGenerator.getInstance("AES");
        g.init(256);
        SecretKey key = g.generateKey();
        Files.createDirectories(folder);
        byte[] encoded = Base64.getEncoder().encodeToString(key.getEncoded()).getBytes(StandardCharsets.US_ASCII);
        try {
            // created already with permissions 600: there is no moment in which other users can read it
            Files.write(Files.createFile(keyFile, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))), encoded);
        } catch (UnsupportedOperationException e) {
            // file system without POSIX permissions
            Files.write(keyFile, encoded, java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            // another process created it meanwhile: its key is the one used from now on
            return new SecretKeySpec(Base64.getDecoder().decode(Files.readString(keyFile).trim()), "AES");
        }
        return key;
    }
}
