package com.gyanwire.research.service;

import com.gyanwire.digests.DigestDeduper;
import com.gyanwire.persistence.postgres.model.PageCacheEntity;
import com.gyanwire.persistence.postgres.repository.PageCacheRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class PageCacheService {

    static final int TTL_DAYS = 7;

    private final PageCacheRepository repository;

    public PageCacheService(PageCacheRepository repository) {
        this.repository = repository;
    }

    public void remember(String url, String snippet, String text) {
        try {
            PageCacheEntity row = new PageCacheEntity();
            row.setUrlHash(DigestDeduper.urlHash(url));
            row.setUrl(url);
            row.setSnippet(clip(snippet, 500));
            row.setText(clip(text, 12000));
            row.setFetchedAt(Instant.now());
            repository.save(row);
        } catch (Exception ignored) {
            // Cache misses must not fail the search.
        }
    }

    public boolean fresh(Instant fetchedAt) {
        return fetchedAt != null && fetchedAt.isAfter(Instant.now().minus(TTL_DAYS, ChronoUnit.DAYS));
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
