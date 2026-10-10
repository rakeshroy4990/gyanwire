package com.gyanwire.news;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * One in-flight fetch per industry. A timed-out waiter still leaves that fetch running.
 */
public final class IndustryFetchGate {

    public record Outcome(boolean timedOut, boolean failed) {
        public static Outcome done() {
            return new Outcome(false, false);
        }

        public static Outcome warming() {
            return new Outcome(true, false);
        }

        public static Outcome failure() {
            return new Outcome(false, true);
        }
    }

    private final ConcurrentHashMap<String, CompletableFuture<Outcome>> inflight = new ConcurrentHashMap<>();

    public Outcome share(String industry, Duration timeout, Supplier<Outcome> work, Executor executor) {
        CompletableFuture<Outcome> mine = new CompletableFuture<>();
        CompletableFuture<Outcome> prior = inflight.putIfAbsent(industry, mine);
        if (prior != null) {
            return await(prior, timeout);
        }
        executor.execute(() -> {
            try {
                mine.complete(work.get());
            } catch (Exception ex) {
                mine.complete(Outcome.failure());
            } finally {
                inflight.remove(industry, mine);
            }
        });
        return await(mine, timeout);
    }

    public int inflightCount() {
        return inflight.size();
    }

    private static Outcome await(CompletableFuture<Outcome> future, Duration timeout) {
        try {
            return future.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            return Outcome.warming();
        } catch (Exception ex) {
            return Outcome.failure();
        }
    }
}
