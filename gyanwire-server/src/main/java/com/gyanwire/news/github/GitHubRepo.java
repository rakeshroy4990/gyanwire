package com.gyanwire.news.github;

import java.time.Instant;

/** One repository row from GitHub Search /repositories. */
public record GitHubRepo(
        String fullName,
        String htmlUrl,
        String description,
        String language,
        long stars,
        long forks,
        String licence,
        Instant pushedAt,
        boolean archived,
        boolean fork
) {
    public boolean usable() {
        return fullName != null && !fullName.isBlank()
                && htmlUrl != null && !htmlUrl.isBlank()
                && !archived
                && !fork;
    }
}
