package com.quarkus.cms.media.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response wrapper for file upload operations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadResponse {

  /** Single file upload result. */
  public MediaFileDto file;

  /** Multiple file upload results. */
  public List<MediaFileDto> files;

  /** Error details for failed uploads. */
  public List<UploadError> errors;

  public static UploadResponse single(MediaFileDto file) {
    UploadResponse r = new UploadResponse();
    r.file = file;
    return r;
  }

  public static UploadResponse multiple(List<MediaFileDto> files, List<UploadError> errors) {
    UploadResponse r = new UploadResponse();
    r.files = files;
    r.errors = errors;
    return r;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class UploadError {
    public String fileName;
    public String message;

    public UploadError() {}

    public UploadError(String fileName, String message) {
      this.fileName = fileName;
      this.message = message;
    }
  }
}
