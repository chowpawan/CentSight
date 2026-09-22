package com.centsight.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(name = "google_sub", nullable = false, unique = true)
    private String googleSub;

    @Column(nullable = false)
    private String email;

    private String name;

    @Column(name = "picture_url")
    private String pictureUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_login")
    private Instant lastLogin;

    protected User() { }

    public User(String googleSub, String email, String name, String pictureUrl) {
        this.id = UUID.randomUUID();
        this.googleSub = googleSub;
        this.email = email;
        this.name = name;
        this.pictureUrl = pictureUrl;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getGoogleSub() { return googleSub; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getPictureUrl() { return pictureUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLogin() { return lastLogin; }

    public void setEmail(String email) { this.email = email; }
    public void setName(String name) { this.name = name; }
    public void setPictureUrl(String pictureUrl) { this.pictureUrl = pictureUrl; }
    public void setLastLogin(Instant lastLogin) { this.lastLogin = lastLogin; }
}
