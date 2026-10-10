package com.gyanwire.news;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class NewsFetchCoordinator {

    public enum Mode { STARTUP, SCHEDULED, ON_DEMAND, MANUAL }

    private final IndustryFetchGate gate = new IndustryFetchGate();
    private final ExecutorService workers = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "news-ingest");
        thread.setDaemon(true);
        return thread;
    });
    private final NewsIngestService ingest;

    public NewsFetchCoordinator(NewsIngestService ingest) {
        this.ingest = ingest;
    }

    public IndustryFetchGate.Outcome fetch(String industry, String sub, Mode mode, Duration timeout) {
        if ((mode == Mode.STARTUP || mode == Mode.SCHEDULED) && ingest.fresh(industry, Instant.now())) {
            return IndustryFetchGate.Outcome.done();
        }
        return gate.share(industry, timeout, () -> ingest.run(industry, sub), workers);
    }
}
