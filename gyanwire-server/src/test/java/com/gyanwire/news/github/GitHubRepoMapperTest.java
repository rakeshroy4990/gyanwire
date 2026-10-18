package com.gyanwire.news.github;

import com.gyanwire.news.NewsRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubRepoMapperTest {

    @Test
    void mapsFamousRepoIntoIdeaReadyNewsRow() {
        GitHubRepo repo = new GitHubRepo(
                "vercel/next.js",
                "https://github.com/vercel/next.js",
                "The React Framework",
                "JavaScript",
                130000,
                28000,
                "MIT",
                Instant.parse("2026-10-10T00:00:00Z"),
                false,
                false);
        Instant now = Instant.parse("2026-10-18T06:00:00Z");
        NewsRow row = GitHubRepoMapper.toNewsRow(
                repo, "GitHub", "Stars", UUID.randomUUID(), "GitHub Search", 1.5, now);

        assertNotNull(row);
        assertEquals("GitHub", row.industry());
        assertEquals("Stars", row.sub());
        assertEquals("github.com", row.domain());
        assertEquals(now, row.publishedAt());
        assertFalse(row.dateEstimated());
        assertTrue(row.title().contains("vercel/next.js"));
        assertTrue(row.summary().toLowerCase().contains("stars"));
        assertTrue(row.summary().toLowerCase().contains("open-source"));
        assertEquals("stars", row.signalType());
        assertTrue(row.opportunityScore() >= 88);
        assertTrue(row.summary().length() >= 120);
    }

    @Test
    void opportunityScoreScalesWithStars() {
        assertEquals(95, GitHubRepoMapper.opportunityScore(150_000));
        assertEquals(78, GitHubRepoMapper.opportunityScore(25_000));
        assertEquals(55, GitHubRepoMapper.opportunityScore(2_000));
    }
}
