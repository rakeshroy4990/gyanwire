package com.gyanwire.research.engine;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpFetchClientTest {

    @Test
    void redirectOntoLoopbackIsRejected() throws Exception {
        InetAddress publicIp = InetAddress.getByName("93.184.216.34");
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        HttpFetchClient client = new HttpFetchClient(
                host -> host.equals("127.0.0.1") ? new InetAddress[]{loopback} : new InetAddress[]{publicIp},
                (uri, userAgent, timeoutMs) -> new HttpFetchClient.FetchResult(302, "http://127.0.0.1/secret", new byte[0]),
                0
        );

        assertThatThrownBy(() -> client.get("http://example.com/start", "GyanwireBot", 8000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Blocked");
    }

    @Test
    void oversizedBodyIsRejected() {
        byte[] huge = new byte[UrlGuard.MAX_BODY_BYTES + 1];
        HttpFetchClient client = new HttpFetchClient(
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                (uri, userAgent, timeoutMs) -> new HttpFetchClient.FetchResult(200, null, huge),
                0
        );

        assertThatThrownBy(() -> client.get("https://example.com/big", "GyanwireBot", 8000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 MB");
    }

    @Test
    void fourthRedirectIsRejected() {
        AtomicInteger hops = new AtomicInteger();
        HttpFetchClient client = new HttpFetchClient(
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                (uri, userAgent, timeoutMs) -> {
                    int hop = hops.incrementAndGet();
                    return new HttpFetchClient.FetchResult(302, "https://example.com/hop-" + hop, new byte[0]);
                },
                0
        );

        assertThatThrownBy(() -> client.get("https://example.com/start", "GyanwireBot", 8000))
                .isInstanceOf(IllegalStateException.class);
        assertThat(hops.get()).isEqualTo(UrlGuard.MAX_REDIRECTS + 1);
    }

    @Test
    void successReturnsBody() throws Exception {
        HttpFetchClient client = new HttpFetchClient(
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                (uri, userAgent, timeoutMs) -> new HttpFetchClient.FetchResult(
                        200, null, "Quarterly results".getBytes(StandardCharsets.UTF_8)),
                0
        );

        assertThat(client.get("https://example.com/ok", "GyanwireBot", 30_000)).isEqualTo("Quarterly results");
    }
}
