package com.ringout.api.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.aws.cloudfront")
public record CloudFrontProperties(
    String baseUrl
) {
}
