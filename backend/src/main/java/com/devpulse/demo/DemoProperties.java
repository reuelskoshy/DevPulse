package com.devpulse.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code devpulse.demo.enabled} (env {@code DEMO_ENABLED}) turns the public live demo on. Off by default. */
@ConfigurationProperties(prefix = "devpulse.demo")
public record DemoProperties(boolean enabled) {
}
