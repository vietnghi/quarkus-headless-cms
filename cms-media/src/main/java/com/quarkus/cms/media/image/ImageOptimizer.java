package com.quarkus.cms.media.image;

import com.quarkus.cms.media.config.MediaConfig;
import com.quarkus.cms.media.entity.CmsFile;
import com.quarkus.cms.media.storage.StorageProvider;

import io.quarkus.logging.Log;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Image optimization service: resize, thumbnail generation, and format conversion.
 *
 * <p>Uses Java's built-in {@link ImageIO} for image processing. Supports JPEG, PNG, GIF, and
 * WebP (if the platform has a WebP ImageIO plugin).
 */
@ApplicationScoped
public class ImageOptimizer {

  @Inject MediaConfig config;

  /** Parsed thumbnail sizes (lazy-loaded). */
  private volatile List<ThumbnailSize> thumbnailSizes;

  /** Parsed responsive breakpoints (lazy-loaded). */
  private volatile List<Breakpoint> breakpoints;

  /**
   * Generates all configured thumbnail variants for an uploaded image.
   *
   * @param imageBytes the original image bytes
   * @param formatName the original format (e.g., "jpg", "png")
   * @return list of generated variants
   */
  public List<VariantResult> generateThumbnails(byte[] imageBytes, String formatName) {
    List<VariantResult> results = new ArrayList<>();
    if (!config.image().enabled()) {
      return results;
    }

    BufferedImage source;
    try {
      source = ImageIO.read(new ByteArrayInputStream(imageBytes));
    } catch (Exception e) {
      Log.warnf("Failed to read image for thumbnail generation: %s", e.getMessage());
      return results;
    }
    if (source == null) {
      Log.warn("ImageIO could not decode the image — unsupported format or corrupt data");
      return results;
    }

    int maxDim = config.image().maxSourceDimension();
    BufferedImage working = source;
    if (source.getWidth() > maxDim || source.getHeight() > maxDim) {
      working = resizeToFit(source, maxDim, maxDim);
    }

    for (ThumbnailSize size : getThumbnailSizes()) {
      try {
        VariantResult variant = generateVariant(working, size.name(), size.width(), size.height(),
            formatName);
        if (variant != null) {
          results.add(variant);
        }
      } catch (Exception e) {
        Log.warnf("Failed to generate thumbnail '%s': %s", size.name(), e.getMessage());
      }
    }

    return results;
  }

  /**
   * Generates responsive image variants at the configured breakpoint widths.
   *
   * @param imageBytes the original image bytes
   * @param formatName the original format
   * @return list of responsive variants (keyed by breakpoint name)
   */
  public List<VariantResult> generateResponsiveVariants(byte[] imageBytes, String formatName) {
    List<VariantResult> results = new ArrayList<>();
    if (!config.image().enabled()) {
      return results;
    }

    BufferedImage source;
    try {
      source = ImageIO.read(new ByteArrayInputStream(imageBytes));
    } catch (Exception e) {
      Log.warnf("Failed to read image for responsive variants: %s", e.getMessage());
      return results;
    }
    if (source == null) {
      return results;
    }

    for (Breakpoint bp : getBreakpoints()) {
      if (bp.width() >= source.getWidth()) {
        continue; // Don't upscale
      }
      try {
        int height = (int) ((double) bp.width() / source.getWidth() * source.getHeight());
        VariantResult variant = generateVariant(source, bp.name(), bp.width(), height,
            formatName);
        if (variant != null) {
          results.add(variant);
        }
      } catch (Exception e) {
        Log.warnf("Failed to generate responsive variant '%s': %s", bp.name(), e.getMessage());
      }
    }

    return results;
  }

  /**
   * Extracts image dimensions from raw bytes.
   *
   * @return [width, height] or {@code null} if not an image
   */
  public int[] getDimensions(byte[] imageBytes) {
    try {
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
      if (img != null) {
        return new int[] {img.getWidth(), img.getHeight()};
      }
    } catch (Exception e) {
      // Not an image or unsupported format
    }
    return null;
  }

  /**
   * Converts an image to the target format (e.g., convert PNG to WebP).
   */
  public byte[] convert(byte[] imageBytes, String targetFormat) {
    try {
      BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
      if (source == null) {
        return imageBytes;
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(source, targetFormat, out);
      return out.toByteArray();
    } catch (Exception e) {
      Log.warnf("Format conversion to %s failed: %s", targetFormat, e.getMessage());
      return imageBytes;
    }
  }

  private VariantResult generateVariant(BufferedImage source, String name, int targetWidth,
      int targetHeight, String originalFormat) {
    BufferedImage resized = resizeToFit(source, targetWidth, targetHeight);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    String outputFormat = detectOutputFormat(originalFormat);
    try {
      if ("jpeg".equalsIgnoreCase(outputFormat) || "jpg".equalsIgnoreCase(outputFormat)) {
        writeJpeg(resized, out, config.image().quality());
      } else if ("webp".equalsIgnoreCase(outputFormat)) {
        // Fall back to PNG if WebP support isn't available
        if (!ImageIO.getImageWritersByFormatName("webp").hasNext()) {
          ImageIO.write(resized, "png", out);
          outputFormat = "png";
        } else {
          writeWithQuality(resized, out, outputFormat, config.image().quality());
        }
      } else {
        ImageIO.write(resized, outputFormat, out);
      }
    } catch (Exception e) {
      Log.warnf("Failed to write variant '%s': %s", name, e.getMessage());
      return null;
    }

    byte[] bytes = out.toByteArray();
    String mime = mapFormatToMime(outputFormat);
    return new VariantResult(name, targetWidth, targetHeight, bytes.length, mime, bytes,
        outputFormat);
  }

  private BufferedImage resizeToFit(BufferedImage source, int maxWidth, int maxHeight) {
    int srcW = source.getWidth();
    int srcH = source.getHeight();

    double scale = Math.min((double) maxWidth / srcW, (double) maxHeight / srcH);
    if (scale >= 1.0) {
      return source;
    }

    int newW = (int) (srcW * scale);
    int newH = (int) (srcH * scale);

    BufferedImage resized = new BufferedImage(newW, newH,
        source.getTransparency() == BufferedImage.OPAQUE
            ? BufferedImage.TYPE_INT_RGB
            : BufferedImage.TYPE_INT_ARGB);

    Graphics2D g = resized.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.drawImage(source, 0, 0, newW, newH, null);
    g.dispose();

    return resized;
  }

  private void writeJpeg(BufferedImage image, ByteArrayOutputStream out, int quality) {
    try {
      ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(quality / 100f);
      ImageOutputStream ios = ImageIO.createImageOutputStream(out);
      writer.setOutput(ios);
      writer.write(null, new IIOImage(image, null, null), param);
      writer.dispose();
      ios.close();
    } catch (Exception e) {
      throw new RuntimeException("Failed to write JPEG", e);
    }
  }

  private void writeWithQuality(BufferedImage image, ByteArrayOutputStream out, String format,
      int quality) {
    try {
      ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
      ImageWriteParam param = writer.getDefaultWriteParam();
      if (param.canWriteCompressed()) {
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality / 100f);
      }
      ImageOutputStream ios = ImageIO.createImageOutputStream(out);
      writer.setOutput(ios);
      writer.write(null, new IIOImage(image, null, null), param);
      writer.dispose();
      ios.close();
    } catch (Exception e) {
      throw new RuntimeException("Failed to write " + format, e);
    }
  }

  private String detectOutputFormat(String originalFormat) {
    if (originalFormat == null) {
      return "jpeg";
    }
    return switch (originalFormat.toLowerCase()) {
      case "png", "gif" -> "png";
      case "webp" -> "webp";
      default -> "jpeg";
    };
  }

  private String mapFormatToMime(String format) {
    return switch (format.toLowerCase()) {
      case "jpeg", "jpg" -> "image/jpeg";
      case "png" -> "image/png";
      case "gif" -> "image/gif";
      case "webp" -> "image/webp";
      default -> "application/octet-stream";
    };
  }

  // ---- Config parsing ----

  private List<ThumbnailSize> getThumbnailSizes() {
    if (thumbnailSizes == null) {
      synchronized (this) {
        if (thumbnailSizes == null) {
          thumbnailSizes = parseThumbnailSizes(config.image().thumbnailSizes());
        }
      }
    }
    return thumbnailSizes;
  }

  private List<Breakpoint> getBreakpoints() {
    if (breakpoints == null) {
      synchronized (this) {
        if (breakpoints == null) {
          breakpoints = parseBreakpoints(config.image().responsiveBreakpoints());
        }
      }
    }
    return breakpoints;
  }

  static List<ThumbnailSize> parseThumbnailSizes(String raw) {
    List<ThumbnailSize> sizes = new ArrayList<>();
    if (raw == null || raw.isBlank()) {
      return sizes;
    }
    for (String part : raw.split(",")) {
      String[] fields = part.trim().split(":");
      if (fields.length == 3) {
        try {
          sizes.add(new ThumbnailSize(fields[0].trim(), Integer.parseInt(fields[1].trim()),
              Integer.parseInt(fields[2].trim())));
        } catch (NumberFormatException e) {
          Log.warnf("Invalid thumbnail size entry: %s", part);
        }
      }
    }
    return sizes;
  }

  static List<Breakpoint> parseBreakpoints(String raw) {
    List<Breakpoint> bps = new ArrayList<>();
    if (raw == null || raw.isBlank()) {
      return bps;
    }
    for (String part : raw.split(",")) {
      String[] fields = part.trim().split(":");
      if (fields.length == 2) {
        try {
          bps.add(new Breakpoint(fields[0].trim(), Integer.parseInt(fields[1].trim())));
        } catch (NumberFormatException e) {
          Log.warnf("Invalid breakpoint entry: %s", part);
        }
      }
    }
    bps.sort((a, b) -> Integer.compare(a.width(), b.width()));
    return bps;
  }

  // ---- Inner types ----

  /** Parsed thumbnail size preset. */
  public record ThumbnailSize(String name, int width, int height) {}

  /** Parsed responsive breakpoint. */
  public record Breakpoint(String name, int width) {}

  /** Result of generating an image variant. */
  public record VariantResult(String name, int width, int height, long size, String mimeType,
      byte[] bytes, String format) {}
}
