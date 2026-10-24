import com.mediarenamer.ui.Logo;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Generates the PNG logo and the executable's .ico icon from com.mediarenamer.ui.Logo.
 *
 * Usage (after compiling):  java -cp target/classes tools/GenerateIcons.java
 * Output: docs/img/logo-*.png and src/main/packaging/MediaRenamer.ico
 */
public class GenerateIcons {

    public static void main(String[] args) throws IOException {
        Path img = Path.of("docs", "img");
        Path packaging = Path.of("src", "main", "packaging");
        Files.createDirectories(img);
        Files.createDirectories(packaging);

        for (int s : new int[] { 64, 128, 256, 512 }) {
            ImageIO.write(Logo.image(s), "png", img.resolve("logo-" + s + ".png").toFile());
        }

        // ICO with PNG entries (format supported since Windows Vista)
        int[] sizes = { 16, 20, 24, 32, 40, 48, 64, 128, 256 };
        List<byte[]> pngs = new ArrayList<>();
        for (int s : sizes) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BufferedImage im = Logo.image(s);
            ImageIO.write(im, "png", out);
            pngs.add(out.toByteArray());
        }
        ByteArrayOutputStream ico = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            header.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s)).put((byte) 0).put((byte) 0);
            header.putShort((short) 1).putShort((short) 32);
            header.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        try (DataOutputStream d = new DataOutputStream(ico)) {
            d.write(header.array());
            for (byte[] p : pngs) {
                d.write(p);
            }
        }
        Files.write(packaging.resolve("MediaRenamer.ico"), ico.toByteArray());
        System.out.println("OK: docs/img/logo-*.png and src/main/packaging/MediaRenamer.ico");
    }
}
