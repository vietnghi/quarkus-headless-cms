package com.quarkus.cms.media.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImageOptimizerTest {

    private final ImageOptimizer optimizer = new ImageOptimizer();

    @Test
    void shouldParseThumbnailSizes() {
        String raw = "thumbnail:150:150,small:300:300,medium:600:600";
        List<ImageOptimizer.ThumbnailSize> sizes = ImageOptimizer.parseThumbnailSizes(raw);

        assertEquals(3, sizes.size());
        assertEquals("thumbnail", sizes.get(0).name());
        assertEquals(150, sizes.get(0).width());
        assertEquals(150, sizes.get(0).height());
        assertEquals("small", sizes.get(1).name());
        assertEquals("medium", sizes.get(2).name());
    }

    @Test
    void shouldParseThumbnailSizesEmpty() {
        assertTrue(ImageOptimizer.parseThumbnailSizes(null).isEmpty());
        assertTrue(ImageOptimizer.parseThumbnailSizes("").isEmpty());
        assertTrue(ImageOptimizer.parseThumbnailSizes("  ").isEmpty());
    }

    @Test
    void shouldParseBreakpoints() {
        String raw = "xs:320,sm:640,md:768,lg:1024";
        List<ImageOptimizer.Breakpoint> bps = ImageOptimizer.parseBreakpoints(raw);

        assertEquals(4, bps.size());
        assertEquals("xs", bps.get(0).name());
        assertEquals(320, bps.get(0).width());
        assertEquals("sm", bps.get(1).name());
        // Should be sorted by width
        assertEquals(320, bps.get(0).width());
        assertEquals(640, bps.get(1).width());
        assertEquals(768, bps.get(2).width());
        assertEquals(1024, bps.get(3).width());
    }

    @Test
    void shouldParseBreakpointsEmpty() {
        assertTrue(ImageOptimizer.parseBreakpoints(null).isEmpty());
        assertTrue(ImageOptimizer.parseBreakpoints("").isEmpty());
    }

    @Test
    void shouldGetDimensions() throws IOException {
        byte[] imageData = createTestImage(800, 600);
        int[] dims = optimizer.getDimensions(imageData);

        assertNotNull(dims);
        assertEquals(800, dims[0]);
        assertEquals(600, dims[1]);
    }

    @Test
    void shouldReturnNullForNonImageData() {
        int[] dims = optimizer.getDimensions(new byte[]{0x00, 0x01, 0x02});
        assertNull(dims);
    }

    @Test
    void shouldConvertImage() throws IOException {
        byte[] pngData = createTestImage(200, 150, "png");
        byte[] jpegData = optimizer.convert(pngData, "jpeg");

        assertNotNull(jpegData);
        assertTrue(jpegData.length > 0);
        // Verify it's actually a JPEG
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpegData));
        assertNotNull(img);
        assertEquals(200, img.getWidth());
        assertEquals(150, img.getHeight());
    }

    @Test
    void shouldReturnOriginalOnConvertFailure() {
        byte[] data = new byte[]{0x01, 0x02, 0x03};
        byte[] result = optimizer.convert(data, "jpeg");
        assertArrayEquals(data, result);
    }

    private byte[] createTestImage(int width, int height) throws IOException {
        return createTestImage(width, height, "jpeg");
    }

    private byte[] createTestImage(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.drawLine(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
