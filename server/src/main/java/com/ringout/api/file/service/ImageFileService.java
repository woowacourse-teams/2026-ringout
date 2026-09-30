package com.ringout.api.file.service;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.file.domain.ImageFile;
import com.ringout.api.file.repository.ImageFileRepository;
import com.ringout.api.file.status.FileErrorStatus;
import com.ringout.api.file.storage.ImageStorage;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ImageFileService {

    private final ImageStorage imageStorage;
    private final ImageFileRepository imageFileRepository;

    @Transactional
    public ImageFile upload(MultipartFile image, String directory) {
        String objectKey = imageStorage.upload(image, directory);
        boolean rollbackCleanupRegistered = registerRollbackCleanup(objectKey);
        try {
            return imageFileRepository.saveAndFlush(ImageFile.from(objectKey));
        } catch (RuntimeException exception) {
            if (!rollbackCleanupRegistered) {
                imageStorage.delete(objectKey);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public ImageFile findById(Long imageFileId) {
        return imageFileRepository.findById(imageFileId)
            .orElseThrow(() -> new GeneralException(FileErrorStatus.IMAGE_FILE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public URI createReadUri(Long imageFileId) {
        return createReadUri(findById(imageFileId));
    }

    public URI createReadUri(ImageFile imageFile) {
        if (imageFile == null) {
            throw new GeneralException(FileErrorStatus.IMAGE_FILE_NOT_FOUND);
        }
        return imageStorage.createReadUri(imageFile.getUrl());
    }

    @Transactional
    public void delete(ImageFile imageFile) {
        if (imageFile == null) {
            return;
        }
        imageFileRepository.delete(imageFile);
        deleteStorageAfterCommit(imageFile.getUrl());
    }

    private boolean registerRollbackCleanup(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    imageStorage.delete(objectKey);
                }
            }
        });
        return true;
    }

    private void deleteStorageAfterCommit(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            imageStorage.delete(objectKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageStorage.delete(objectKey);
            }
        });
    }
}
