package com.ringout.api.file.domain;

import com.ringout.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "image_file")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageFile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String url;

    private ImageFile(String url) {
        this.url = url;
    }

    public static ImageFile from(String url) {
        return new ImageFile(url);
    }
}
