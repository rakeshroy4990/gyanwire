package com.gyanwire.ideas;

import java.util.Map;

/**
 * Receives idea-run events as they become available so NDJSON can flush
 * each line before the next pipeline stage starts.
 */
public interface IdeaSink {

    void status(String stage);

    void meta(Map<String, Object> frame);

    void item(Map<String, Object> row);

    /** Background video-research progress (started / ready / failed). */
    default void research(Map<String, Object> frame) {
    }

    static IdeaSink noop() {
        return Noop.INSTANCE;
    }

    static IdeaSink tee(IdeaSink first, IdeaSink second) {
        return new Tee(first == null ? noop() : first, second == null ? noop() : second);
    }

    final class Noop implements IdeaSink {
        private static final Noop INSTANCE = new Noop();

        private Noop() {
        }

        @Override
        public void status(String stage) {
        }

        @Override
        public void meta(Map<String, Object> frame) {
        }

        @Override
        public void item(Map<String, Object> row) {
        }
    }

    final class Tee implements IdeaSink {
        private final IdeaSink first;
        private final IdeaSink second;

        Tee(IdeaSink first, IdeaSink second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void status(String stage) {
            first.status(stage);
            second.status(stage);
        }

        @Override
        public void meta(Map<String, Object> frame) {
            first.meta(frame);
            second.meta(frame);
        }

        @Override
        public void item(Map<String, Object> row) {
            first.item(row);
            second.item(row);
        }

        @Override
        public void research(Map<String, Object> frame) {
            first.research(frame);
            second.research(frame);
        }
    }
}
