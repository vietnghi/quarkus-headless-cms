package com.quarkus.cms.media.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Media library configuration, mapped from {@code quarkus.cms.media.*} properties.
 *
 * <p>This is a standalone config interface for the cms-media module, reading the same
 * properties as {@code CmsConfig.MediaConfig} in the runtime module.
 */
@ConfigMapping(prefix = "quarkus.cms.media")
public interface MediaConfig {

  /**
   * Maximum upload file size, e.g. "10M".
   */
  @WithDefault("10M")
  String maxUploadSize();

  /**
   * Storage provider: "local", "s3", or "r2".
   */
  @WithDefault("local")
  String storageProvider();

  /**
   * Upload directory for local storage.
   */
  @WithDefault("uploads")
  String uploadDirectory();

  /**
   * Comma-separated list of allowed MIME types.
   */
  @WithName("allowed-types")
  @WithDefault("image/jpeg,image/png,image/gif,image/webp,image/svg+xml,"
      + "application/pdf,text/plain,text/csv,"
      + "application/msword,"
      + "application/vnd.openxmlformats-officedocument.wordprocessingml.document,"
      + "application/vnd.ms-excel,"
      + "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,"
      + "video/mp4,video/webm,audio/mpeg,audio/ogg")
  String allowedTypes();

  /**
   * S3 / R2 configuration.
   */
  @WithName("s3")
  S3Config s3();

  /**
   * Image processing configuration.
   */
  @WithName("image")
  ImageConfig image();

  interface S3Config {
    @WithDefault("cms-media")
    String bucket();

    Optional<String> endpoint();

    @WithDefault("us-east-1")
    String region();

    Optional<String> accessKeyId();

    Optional<String> secretAccessKey();

    Optional<String> publicUrlPrefix();
  }

  interface ImageConfig {
    @WithDefault("true")
    boolean enabled();

    @WithDefault("85")
    int quality();

    @WithName("thumbnail-sizes")
    @WithDefault("thumbnail:150:150,small:300:300,medium:600:600,large:1200:1200")
    String thumbnailSizes();

    @WithName("responsive-breakpoints")
    @WithDefault("xs:320,sm:640,md:768,lg:1024,xl:1280,xxl:1536")
    String responsiveBreakpoints();

    @WithName("max-source-dimension")
    @WithDefault("4096")
    int maxSourceDimension();
  }
}
