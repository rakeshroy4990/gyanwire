package com.gyanwire.usage;

import java.util.UUID;

public interface SpendGuard {
    boolean allow(UUID userId);
}
