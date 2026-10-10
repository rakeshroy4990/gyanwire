package com.gyanwire.news;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "gyanwire.news.jobs.enabled", havingValue = "true")
public class NewsFeedbackJob {

    private static final Logger log = LoggerFactory.getLogger(NewsFeedbackJob.class);

    private final NewsStore store;

    public NewsFeedbackJob(NewsStore store) {
        this.store = store;
    }

    @Scheduled(cron = "${gyanwire.news.jobs.feedback-cron:0 0 6 * * MON}")
    public void weekly() {
        try {
            log.info("News usefulness {}", store.feedbackBySignal());
        } catch (Exception ex) {
            log.info("News usefulness skipped: {}", ex.getMessage());
        }
    }
}
