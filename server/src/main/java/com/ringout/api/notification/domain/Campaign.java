package com.ringout.api.notification.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    private String deeplink;

    @Enumerated(EnumType.STRING)
    private CampaignTargetType campaignTargetType;

    private Campaign(String title, String body, String deeplink, CampaignTargetType campaignTargetType) {
        this.title = title;
        this.body = body;
        this.deeplink = deeplink;
        this.campaignTargetType = campaignTargetType;
    }

    public static Campaign of(String title, String body, String deeplink,
        CampaignTargetType campaignTargetType) {
        return new Campaign(title, body, deeplink, campaignTargetType);
    }
}
