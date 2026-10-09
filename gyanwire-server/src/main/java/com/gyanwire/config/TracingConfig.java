package com.gyanwire.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfig {

    @PostConstruct
    void install() {
        try {
            SdkTracerProvider provider = SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
                    .build();
            OpenTelemetrySdk.builder().setTracerProvider(provider).buildAndRegisterGlobal();
        } catch (IllegalStateException ignored) {
            // Another class loader already registered a global tracer.
        }
    }

    public static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("gyanwire");
    }
}
