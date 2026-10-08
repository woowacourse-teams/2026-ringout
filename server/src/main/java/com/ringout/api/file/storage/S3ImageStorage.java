package com.ringout.api.file.storage;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.file.config.CloudFrontProperties;
import com.ringout.api.file.config.S3Properties;
import com.ringout.api.file.status.FileErrorStatus;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Pattern DIRECTORY_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9/_-]*$");
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");

    private final S3Client s3Client;
    private final S3Properties properties;
    private final CloudFrontProperties cloudFrontProperties;

    @Override
    public String upload(MultipartFile image, String directory) {
        validateImage(image);
        String normalizedDirectory = normalizeDirectory(directory);
        String objectKey = normalizedDirectory + "/" + UUID.randomUUID() + resolveExtension(image);

        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(requireBucket(FileErrorStatus.IMAGE_UPLOAD_FAILED))
            .key(objectKey)
            .contentType(image.getContentType())
            .contentLength(image.getSize())
            .build();

        try (InputStream inputStream = image.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, image.getSize()));
            return objectKey;
        } catch (IOException | SdkException exception) {
            log.error("S3 이미지 업로드 실패. objectKey={}", objectKey, exception);
            throw new GeneralException(FileErrorStatus.IMAGE_UPLOAD_FAILED);
        }
    }

    @Override
    public URI createReadUri(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new GeneralException(FileErrorStatus.IMAGE_FILE_INVALID);
        }

        try {
            URI baseUri = requireCloudFrontBaseUri();
            String baseUrl = baseUri.toString();
            String separator = baseUrl.endsWith("/") ? "" : "/";
            return URI.create(baseUrl + separator + objectKey);
        } catch (IllegalArgumentException exception) {
            log.error("CloudFront 이미지 조회 URL 생성 실패. objectKey={}", objectKey, exception);
            throw new GeneralException(FileErrorStatus.IMAGE_URL_CREATE_FAILED);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(requireBucket(FileErrorStatus.IMAGE_UPLOAD_FAILED))
                .key(objectKey)
                .build());
        } catch (RuntimeException exception) {
            log.error("S3 이미지 삭제 실패. objectKey={}", objectKey, exception);
        }
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty() || image.getSize() > MAX_IMAGE_SIZE) {
            throw new GeneralException(FileErrorStatus.IMAGE_FILE_INVALID);
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new GeneralException(FileErrorStatus.IMAGE_FILE_INVALID);
        }
    }

    private String normalizeDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            throw new GeneralException(FileErrorStatus.IMAGE_DIRECTORY_INVALID);
        }
        String normalized = directory.endsWith("/")
            ? directory.substring(0, directory.length() - 1)
            : directory;
        if (!DIRECTORY_PATTERN.matcher(normalized).matches()
            || normalized.contains("//")
            || normalized.contains("..")) {
            throw new GeneralException(FileErrorStatus.IMAGE_DIRECTORY_INVALID);
        }
        return normalized;
    }

    private String resolveExtension(MultipartFile image) {
        return Optional.ofNullable(StringUtils.getFilenameExtension(image.getOriginalFilename()))
            .filter(extension -> EXTENSION_PATTERN.matcher(extension).matches())
            .map(extension -> "." + extension.toLowerCase(Locale.ROOT))
            .orElse("");
    }

    private String requireBucket(FileErrorStatus errorStatus) {
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            log.error("AWS_S3_BUCKET 설정이 비어 있습니다.");
            throw new GeneralException(errorStatus);
        }
        return properties.bucket();
    }

    private URI requireCloudFrontBaseUri() {
        String baseUrl = cloudFrontProperties.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("AWS_CLOUDFRONT_BASE_URL 설정이 비어 있습니다.");
        }
        URI baseUri = URI.create(baseUrl.trim());
        if (!baseUri.isAbsolute() || baseUri.getHost() == null
            || !"https".equalsIgnoreCase(baseUri.getScheme())) {
            throw new IllegalArgumentException("AWS_CLOUDFRONT_BASE_URL은 유효한 HTTPS URL이어야 합니다.");
        }
        return baseUri;
    }
}
