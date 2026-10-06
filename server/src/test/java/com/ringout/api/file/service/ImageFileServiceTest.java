package com.ringout.api.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.file.domain.ImageFile;
import com.ringout.api.file.repository.ImageFileRepository;
import com.ringout.api.file.status.FileErrorStatus;
import com.ringout.api.file.storage.ImageStorage;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ImageFileServiceTest {

    @Mock
    private ImageStorage imageStorage;

    @Mock
    private ImageFileRepository imageFileRepository;

    private ImageFileService imageFileService;

    @BeforeEach
    void setUp() {
        imageFileService = new ImageFileService(imageStorage, imageFileRepository);
    }

    @Test
    void 이미지를_업로드하고_S3_key를_저장한다() {
        MockMultipartFile image = new MockMultipartFile(
            "image", "profile.png", "image/png", new byte[]{1, 2, 3}
        );
        String objectKey = "images/profiles/generated.png";
        given(imageStorage.upload(image, "images/profiles")).willReturn(objectKey);
        given(imageFileRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(ImageFile.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        ImageFile savedImage = imageFileService.upload(image, "images/profiles");

        assertThat(savedImage.getUrl()).isEqualTo(objectKey);
    }

    @Test
    void DB_저장에_실패하면_업로드한_S3_객체를_삭제한다() {
        MockMultipartFile image = new MockMultipartFile(
            "image", "profile.png", "image/png", new byte[]{1}
        );
        String objectKey = "images/profiles/generated.png";
        given(imageStorage.upload(image, "images/profiles")).willReturn(objectKey);
        given(imageFileRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(ImageFile.class)))
            .willThrow(new IllegalStateException("DB error"));

        assertThatThrownBy(() -> imageFileService.upload(image, "images/profiles"))
            .isInstanceOf(IllegalStateException.class);
        verify(imageStorage).delete(objectKey);
    }

    @Test
    void 저장된_이미지의_조회_URL을_생성한다() {
        ImageFile imageFile = ImageFile.from("images/profiles/generated.png");
        URI readUri = URI.create("https://example.com/generated.png?signature=test");
        given(imageFileRepository.findById(1L)).willReturn(Optional.of(imageFile));
        given(imageStorage.createReadUri(imageFile.getUrl())).willReturn(readUri);

        assertThat(imageFileService.createReadUri(1L)).isEqualTo(readUri);
    }

    @Test
    void 저장되지_않은_이미지는_조회할_수_없다() {
        given(imageFileRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> imageFileService.createReadUri(1L))
            .isInstanceOfSatisfying(GeneralException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(FileErrorStatus.IMAGE_FILE_NOT_FOUND));
    }
}
