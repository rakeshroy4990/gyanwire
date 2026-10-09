package com.gyanwire.persistence.postgres.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "page_cache")
public class PageCacheEntity {

    @Id
    @Column(name = "url_hash")
    private String urlHash;

    private String url;
    private String snippet;
    private String text;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public void setUrlHash(String urlHash) {
        this.urlHash = urlHash;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
