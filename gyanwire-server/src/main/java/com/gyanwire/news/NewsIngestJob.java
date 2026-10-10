package com.gyanwire.news;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class NewsIngestJob {

    private static final Logger log = LoggerFactory.getLogger(NewsIngestJob.class);
    private static final List<String> FALLBACK = List.of(
            "Share Market", "IT", "Medical", "Space", "Social Media", "Gaming", "Astrology");

    private final NewsProperties properties;
    private final NewsStore store;
    private final NewsFetchCoordinator coordinator;

    public NewsIngestJob(NewsProperties properties, NewsStore store, NewsFetchCoordinator coordinator) {
        this.properties = properties;
        this.store = store;
        this.coordinator = coordinator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!properties.getIngest().isEnabled() || !properties.getIngest().isOnStartup()) {
            return;
        }
        Thread thread = new Thread(this::startup, "news-startup");
        thread.setDaemon(true);
        thread.start();
    }

    @Scheduled(initialDelayString = "${gyanwire.news.ingest.initial-delay:PT5M}", fixedDelayString = "${gyanwire.news.ingest.interval:PT30M}")
    public void poll() {
        if (!properties.getIngest().isEnabled()) {
            return;
        }
        for (String industry : industries()) {
            try {
                coordinator.fetch(industry, null, NewsFetchCoordinator.Mode.SCHEDULED, Duration.ofMinutes(3));
            } catch (Exception ex) {
                log.info("News poll continued after {}: {}", industry, ex.getMessage());
            }
        }
    }

    private void startup() {
        for (String industry : industries()) {
            try {
                coordinator.fetch(industry, null, NewsFetchCoordinator.Mode.STARTUP, Duration.ofMinutes(3));
            } catch (Exception ex) {
                log.info("News startup continued after {}: {}", industry, ex.getMessage());
            }
        }
    }

    private List<String> industries() {
        try {
            List<String> rows = store.industries();
            return rows == null || rows.isEmpty() ? FALLBACK : rows;
        } catch (Exception ex) {
            return FALLBACK;
        }
    }
}
