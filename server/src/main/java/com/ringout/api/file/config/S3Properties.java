package com.ringout.api.file.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.aws.s3")
public record S3Properties(
    String bucket,
    String region,
    Duration presignedUrlExpiration
) {
}
