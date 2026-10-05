package com.gyanwire.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(
                        "classpath:/static/",
                        "file:./dist/",
                        "file:../dist/"
                )
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // SPA fallback when dist is present.
                        Path distIndex = Path.of("dist/index.html");
                        if (Files.exists(distIndex)) {
                            return new org.springframework.core.io.FileSystemResource(distIndex);
                        }
                        Path parentDist = Path.of("../dist/index.html");
                        if (Files.exists(parentDist)) {
                            return new org.springframework.core.io.FileSystemResource(parentDist);
                        }
                        Resource classpathIndex = new ClassPathResource("/static/index.html");
                        return classpathIndex.exists() ? classpathIndex : null;
                    }
                });
    }
}
