package com.ringout.api.file.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ImageFileTest {

    @Test
    void S3_객체_key로_이미지_파일을_생성한다() {
        ImageFile imageFile = ImageFile.from("images/profiles/profile.webp");

        assertThat(imageFile.getUrl()).isEqualTo("images/profiles/profile.webp");
    }

    @Test
    void S3_객체_key가_비어_있으면_생성할_수_없다() {
        assertThatThrownBy(() -> ImageFile.from(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
