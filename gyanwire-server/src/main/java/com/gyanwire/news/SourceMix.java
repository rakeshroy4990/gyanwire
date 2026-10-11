package com.gyanwire.news;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * When a fetch cap is on, keep India sources in most slots and still take worldwide sources.
 * A cap of zero keeps every enabled source.
 */
public final class SourceMix {

    private SourceMix() {
    }

    public static <T> List<T> pick(List<T> sources, int max, Function<T, String> regionFn) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        if (max <= 0 || sources.size() <= max) {
            return List.copyOf(sources);
        }
        List<T> india = new ArrayList<>();
        List<T> world = new ArrayList<>();
        for (T source : sources) {
            if (indiaRegion(regionFn == null ? null : regionFn.apply(source))) {
                india.add(source);
            } else {
                world.add(source);
            }
        }
        int worldSlots = 0;
        if (!world.isEmpty()) {
            worldSlots = india.isEmpty() ? max : Math.max(1, max / 3);
            if (!india.isEmpty() && worldSlots >= max) {
                worldSlots = max - 1;
            }
        }
        int indiaSlots = Math.min(india.size(), max - worldSlots);
        List<T> picked = new ArrayList<>(max);
        picked.addAll(india.subList(0, indiaSlots));
        int worldTake = Math.min(world.size(), max - picked.size());
        picked.addAll(world.subList(0, worldTake));
        if (picked.size() < max) {
            for (T source : india.subList(indiaSlots, india.size())) {
                if (picked.size() >= max) {
                    break;
                }
                picked.add(source);
            }
        }
        if (picked.size() < max) {
            for (T source : world.subList(worldTake, world.size())) {
                if (picked.size() >= max) {
                    break;
                }
                picked.add(source);
            }
        }
        return List.copyOf(picked);
    }

    private static boolean indiaRegion(String region) {
        return region == null || region.isBlank() || "IN".equalsIgnoreCase(region);
    }
}
