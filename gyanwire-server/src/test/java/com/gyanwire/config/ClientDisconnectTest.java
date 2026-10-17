package com.gyanwire.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

class ClientDisconnectTest {

    @Test
    void detectsAsyncRequestNotUsable() {
        assertThat(ClientDisconnect.isGone(new AsyncRequestNotUsableException("gone"))).isTrue();
    }

    @Test
    void detectsBrokenPipeInCauseChain() {
        IOException broken = new IOException("Broken pipe");
        assertThat(ClientDisconnect.isGone(new UncheckedIOException(broken))).isTrue();
    }

    @Test
    void ignoresOrdinaryFailures() {
        assertThat(ClientDisconnect.isGone(new IllegalStateException("database offline"))).isFalse();
        assertThat(ClientDisconnect.isGone(new IOException("disk full"))).isFalse();
    }
}
