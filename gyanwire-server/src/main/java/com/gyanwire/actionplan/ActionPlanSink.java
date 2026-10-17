package com.gyanwire.actionplan;

import java.util.Map;

/**
 * Receives action-plan build events as they become available so NDJSON can
 * flush each line before the next pipeline stage starts.
 */
public interface ActionPlanSink {

    void status(String stage);

    void plan(Map<String, Object> plan);

    static ActionPlanSink noop() {
        return Noop.INSTANCE;
    }

    final class Noop implements ActionPlanSink {
        private static final Noop INSTANCE = new Noop();

        private Noop() {
        }

        @Override
        public void status(String stage) {
        }

        @Override
        public void plan(Map<String, Object> plan) {
        }
    }
}
