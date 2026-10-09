package com.gyanwire.research.engine;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlGuardTest {

    @Test
    void blocksPrivateLoopbackLinkLocalAndMetadata() throws Exception {
        assertThat(UrlGuard.isBlocked(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(UrlGuard.isBlocked(InetAddress.getByName("10.1.2.3"))).isTrue();
        assertThat(UrlGuard.isBlocked(InetAddress.getByName("192.168.1.9"))).isTrue();
        assertThat(UrlGuard.isBlocked(InetAddress.getByName("172.16.0.4"))).isTrue();
        assertThat(UrlGuard.isBlocked(InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(UrlGuard.isBlocked(InetAddress.getByName("169.254.1.1"))).isTrue();
        assertThat(UrlGuard.isBlocked(InetAddress.getByName("8.8.8.8"))).isFalse();
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThatThrownBy(() -> UrlGuard.check(java.net.URI.create("file:///etc/passwd"), host -> new InetAddress[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
