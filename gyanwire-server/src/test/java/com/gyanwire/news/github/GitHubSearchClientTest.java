package com.gyanwire.news.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GitHubSearchClientTest {

    private final GitHubSearchClient client = new GitHubSearchClient(
            new GitHubProperties(), new ObjectMapper(), null);

    @Test
    void prepareQueryDoesNotAddDayWindow() {
        assertEquals("stars:>1000", GitHubSearchClient.prepareQuery("stars:>1000", "Trending"));
        assertEquals("stars:>1000", GitHubSearchClient.prepareQuery("stars:>1000", "Stars"));
        assertEquals("", GitHubSearchClient.prepareQuery("  ", null));
    }

    @Test
    void parseKeepsUsableReposAndDropsForks() throws Exception {
        String body = """
                {
                  "items": [
                    {
                      "full_name": "facebook/react",
                      "html_url": "https://github.com/facebook/react",
                      "description": "The library for web and native user interfaces",
                      "language": "JavaScript",
                      "stargazers_count": 240000,
                      "forks_count": 50000,
                      "license": { "spdx_id": "MIT" },
                      "pushed_at": "2026-10-01T12:00:00Z",
                      "archived": false,
                      "fork": false
                    },
                    {
                      "full_name": "someone/react-fork",
                      "html_url": "https://github.com/someone/react-fork",
                      "description": "fork",
                      "stargazers_count": 10,
                      "forks_count": 0,
                      "archived": false,
                      "fork": true
                    },
                    {
                      "full_name": "old/dead",
                      "html_url": "https://github.com/old/dead",
                      "description": "archived",
                      "stargazers_count": 100,
                      "forks_count": 1,
                      "archived": true,
                      "fork": false
                    }
                  ]
                }
                """;
        List<GitHubRepo> repos = client.parse(body);
        assertEquals(1, repos.size());
        assertEquals("facebook/react", repos.get(0).fullName());
        assertEquals(240000, repos.get(0).stars());
        assertEquals("MIT", repos.get(0).licence());
        assertFalse(repos.get(0).fork());
    }
}
