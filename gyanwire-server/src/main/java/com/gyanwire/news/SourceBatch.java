package com.gyanwire.news;

import java.util.List;
import java.util.function.Consumer;

public final class SourceBatch {

    private SourceBatch() {
    }

    public static void run(List<NewsStore.SourceRow> sources, Consumer<NewsStore.SourceRow> each, Consumer<Exception> onError) {
        if (sources == null) {
            return;
        }
        for (NewsStore.SourceRow source : sources) {
            if (source == null || !source.enabled()) {
                continue;
            }
            try {
                each.accept(source);
            } catch (Exception ex) {
                onError.accept(ex);
            }
        }
    }
}
