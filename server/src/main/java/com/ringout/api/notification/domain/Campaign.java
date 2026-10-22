package com.ringout.api.notification.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "campaign")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> data;

    @Enumerated(EnumType.STRING)
    private CampaignTargetType campaignTargetType;

    private Campaign(String title, String body, Map<String, String> data, CampaignTargetType campaignTargetType) {
        this.title = title;
        this.body = body;
        this.data = data;
        this.campaignTargetType = campaignTargetType;
    }

    public static Campaign of(String title, String body, Map<String, String> data,
        CampaignTargetType campaignTargetType) {
        return new Campaign(title, body, data, campaignTargetType);
    }
}
