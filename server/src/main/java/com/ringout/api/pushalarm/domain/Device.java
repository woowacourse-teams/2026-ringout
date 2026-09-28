package com.ringout.api.pushalarm.domain;

import com.ringout.api.common.BaseEntity;
import com.ringout.api.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "device")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_token", nullable = false, length = 255)
    private String userToken;

    @Column(nullable = false, length = 20)
    private String os;

    private Device(User user, String userToken, String os) {
        this.user = user;
        this.userToken = userToken;
        this.os = os;
    }

    public static Device of(User user, String userToken, String os) {
        return new Device(user, userToken, os);
    }
}
