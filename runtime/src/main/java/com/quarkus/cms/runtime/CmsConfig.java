package com.quarkus.cms.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Runtime configuration for the Quarkus Headless CMS extension. All properties are prefixed with
 * {@code quarkus.cms}.
 */
@ConfigMapping(prefix = "quarkus.cms")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface CmsConfig {

  /** Whether the CMS is enabled. When disabled, all CMS endpoints and services are inactive. */
  @WithDefault("true")
  boolean enabled();

  /** Base path for the public REST API. Default: /api */
  @WithName("api.base-path")
  @WithDefault("/api")
  String apiBasePath();

  /** Base path for the admin panel. Default: /admin */
  @WithName("admin.base-path")
  @WithDefault("/admin")
  String adminBasePath();

  /**
   * Directory containing content-type schema definition files (JSON). Scanned at startup to
   * register dynamic content types.
   */
  @WithName("schema.directory")
  @WithDefault("schemas")
  String schemaDirectory();

  /** Default locale for content entries. */
  @WithName("default-locale")
  @WithDefault("en")
  String defaultLocale();

  /** Media upload configuration. */
  @WithName("media")
  MediaConfig media();

  /** Auth configuration. */
  @WithName("auth")
  AuthConfig auth();

  interface MediaConfig {
    /** Maximum upload file size, e.g. "10M". */
    @WithDefault("10M")
    String maxUploadSize();

    /** Storage provider: "local", "s3", or "r2". */
    @WithDefault("local")
    String storageProvider();

    /** Upload directory for local storage. */
    @WithDefault("uploads")
    String uploadDirectory();

    /**
     * Comma-separated list of allowed MIME types. Default allows common image, document, and video
     * types.
     */
    @WithName("allowed-types")
    @WithDefault(
        "image/jpeg,image/png,image/gif,image/webp,image/svg+xml,"
            + "application/pdf,text/plain,text/csv,"
            + "application/msword,"
            + "application/vnd.openxmlformats-officedocument.wordprocessingml.document,"
            + "application/vnd.ms-excel,"
            + "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,"
            + "video/mp4,video/webm,audio/mpeg,audio/ogg")
    String allowedTypes();

    /** S3 / R2 configuration. */
    @WithName("s3")
    S3Config s3();

    /** Image processing configuration. */
    @WithName("image")
    ImageConfig image();
  }

  interface S3Config {
    /** S3 bucket name. */
    @WithDefault("cms-media")
    String bucket();

    /** S3 endpoint URL (for S3-compatible storage like MinIO or R2). */
    Optional<String> endpoint();

    /** AWS region. */
    @WithDefault("us-east-1")
    String region();

    /** AWS access key ID. */
    Optional<String> accessKeyId();

    /** AWS secret access key. */
    Optional<String> secretAccessKey();

    /** Base URL prefix for public access (e.g., CDN URL). */
    Optional<String> publicUrlPrefix();
  }

  interface ImageConfig {
    /** Whether image optimization (thumbnail generation, format conversion) is enabled. */
    @WithDefault("true")
    boolean enabled();

    /** Default image quality for JPEG/WebP output (1-100). */
    @WithDefault("85")
    int quality();

    /**
     * Thumbnail size presets as comma-separated name:width:height triples. Example:
     * "small:150:150,medium:300:300,large:600:600"
     */
    @WithName("thumbnail-sizes")
    @WithDefault("thumbnail:150:150,small:300:300,medium:600:600,large:1200:1200")
    String thumbnailSizes();

    /**
     * Responsive breakpoints as comma-separated name:width pairs. These define target widths for
     * srcset generation. Example: "sm:640,md:768,lg:1024,xl:1280"
     */
    @WithName("responsive-breakpoints")
    @WithDefault("xs:320,sm:640,md:768,lg:1024,xl:1280,xxl:1536")
    String responsiveBreakpoints();

    /**
     * Maximum source image dimension (width or height) for processing. Images larger than this are
     * downscaled before thumbnail generation.
     */
    @WithName("max-source-dimension")
    @WithDefault("4096")
    int maxSourceDimension();
  }

  interface AuthConfig {
    /** JWT token expiration in seconds. */
    @WithName("jwt.expiration-seconds")
    @WithDefault("86400")
    long jwtExpirationSeconds();

    /** Whether API token authentication is enabled. */
    @WithName("api-tokens.enabled")
    @WithDefault("true")
    boolean apiTokensEnabled();
  }
}
