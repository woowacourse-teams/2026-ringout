package com.ringout.api.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.aws.s3")
public record S3Properties(
    String bucket,
    String region,
    String rootDirectory
) {
}
