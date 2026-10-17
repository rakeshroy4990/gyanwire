package com.gyanwire.config;

import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Detects a client that left while an NDJSON (or other streaming) response
 * was still being written. Those failures are not server bugs.
 */
public final class ClientDisconnect {

    private ClientDisconnect() {
    }

    public static boolean isGone(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String name = current.getClass().getName();
            if (name.endsWith("ClientAbortException") || name.endsWith("EofException")) {
                return true;
            }
            if (current instanceof IOException || current instanceof UncheckedIOException) {
                String message = current.getMessage();
                if (message != null) {
                    String lower = message.toLowerCase();
                    if (lower.contains("broken pipe")
                            || lower.contains("connection reset")
                            || lower.contains("connection aborted")
                            || lower.contains("response not usable")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
