package com.quarkus.cms.admin.api.resource;

import java.util.List;

/**
 * Response wrapper for the global search endpoint.
 */
public class SearchResponse {

    public List<SearchResultItem> results;
    public int total;

    public SearchResponse() {}

    public SearchResponse(List<SearchResultItem> results, int total) {
        this.results = results;
        this.total = total;
    }
}
