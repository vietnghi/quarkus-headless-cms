package com.quarkus.cms.admin.api.resource;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a single search result entry for the admin global search.
 *
 * <p>Each item can be either a content entry (from any content type) or a media file.
 * The {@code contentType} field distinguishes: for content entries it's the content-type
 * UID (e.g. "api::article.article"), for media files it's the literal string "media".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResultItem {

    /** Content-type UID for entries, or "media" for files. */
    public String contentType;

    /** Entity database ID (CmsEntry.id or CmsFile.id). */
    public Long entryId;

    /** Document ID for entries; null for media files. */
    public String documentId;

    /** Entry title or file name. */
    public String title;

    /** Extracted text snippet (first ~150 chars of matched content). */
    public String excerpt;

    /** Admin UI navigation URL for this result. */
    public String url;

    public SearchResultItem() {}

    public SearchResultItem(String contentType, Long entryId, String documentId,
                            String title, String excerpt, String url) {
        this.contentType = contentType;
        this.entryId = entryId;
        this.documentId = documentId;
        this.title = title;
        this.excerpt = excerpt;
        this.url = url;
    }

    public static SearchResultItem forEntry(String contentType, Long entryId,
                                             String documentId, String title,
                                             String excerpt) {
        String url = "/admin/content-manager/collection-types/"
            + contentType + "/" + documentId;
        return new SearchResultItem(contentType, entryId, documentId, title, excerpt, url);
    }

    public static SearchResultItem forMedia(Long fileId, String fileName, String excerpt) {
        String url = "/admin/media/files/" + fileId;
        return new SearchResultItem("media", fileId, null, fileName, excerpt, url);
    }
}
