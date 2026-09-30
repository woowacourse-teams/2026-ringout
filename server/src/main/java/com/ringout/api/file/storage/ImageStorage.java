package com.ringout.api.file.storage;

import java.net.URI;
import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {

    String upload(MultipartFile image, String directory);

    URI createReadUri(String objectKey);

    void delete(String objectKey);
}
