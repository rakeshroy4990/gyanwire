package com.gyanwire.research.service;

import com.gyanwire.digests.DigestDeduper;
import com.gyanwire.persistence.postgres.model.QueryCacheEntity;
import com.gyanwire.persistence.postgres.repository.QueryCacheRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;

@Service
public class QueryCacheService {

    private final QueryCacheRepository repository;

    public QueryCacheService(QueryCacheRepository repository) {
        this.repository = repository;
    }

    public String key(String query, String industry) {
        String normalized = (industry + "|" + String.valueOf(query).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim()
                + "|" + LocalDate.now(ZoneOffset.UTC));
        return DigestDeduper.urlHash(normalized);
    }

    public Map<String, Object> get(String key) {
        try {
            return repository.findById(key)
                    .filter(row -> LocalDate.now(ZoneOffset.UTC).equals(row.getCreatedOn()))
                    .map(QueryCacheEntity::getPayload)
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    public void put(String key, Map<String, Object> payload) {
        try {
            QueryCacheEntity row = new QueryCacheEntity();
            row.setCacheKey(key);
            row.setPayload(payload);
            row.setCreatedOn(LocalDate.now(ZoneOffset.UTC));
            repository.save(row);
        } catch (Exception ignored) {
            // A cache write must not fail the search.
        }
    }
}
