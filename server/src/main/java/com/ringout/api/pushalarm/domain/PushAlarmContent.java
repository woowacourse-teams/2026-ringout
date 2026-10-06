package com.ringout.api.pushalarm.domain;

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
@Table(name = "push_alarm_content")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushAlarmContent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String content;

    private PushAlarmContent(String content) {
        this.content = content;
    }

    public static PushAlarmContent from(String content) {
        return new PushAlarmContent(content);
    }
}
