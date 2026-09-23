package com.devpulse.user.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dp_user")
public class DpUser {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "active_status", nullable = false)
    private Boolean activeStatus;

    @Column(name = "active_status_reason", length = 500)
    private String activeStatusReason;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "location", length = 255)
    private String location;

    @ManyToOne
    @JoinColumn(name = "parent", referencedColumnName = "id")
    private DpUser parent;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private DpUserRole role;

    @Column(name = "account_created_datetime", nullable = false)
    private Instant accountCreatedDatetime;

    @Column(name = "account_modified_datetime", nullable = false)
    private Instant accountModifiedDatetime;

    @Column(name = "last_password_reset")
    private Instant lastPasswordReset;

    @Column(name = "mfa_active", nullable = false)
    private Boolean mfaActive;

    protected DpUser() {
    }

    public DpUser(String name, String email, String password, DpUserRole role) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
        this.activeStatus = true;
        this.mfaActive = false;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        accountCreatedDatetime = now;
        accountModifiedDatetime = now;
    }

    @PreUpdate
    void onUpdate() {
        accountModifiedDatetime = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }

    public Boolean getActiveStatus() { return activeStatus; }
    public void setActiveStatus(Boolean activeStatus) { this.activeStatus = activeStatus; }

    public String getActiveStatusReason() { return activeStatusReason; }
    public void setActiveStatusReason(String activeStatusReason) { this.activeStatusReason = activeStatusReason; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public DpUser getParent() { return parent; }
    public void setParent(DpUser parent) { this.parent = parent; }

    public DpUserRole getRole() { return role; }
    public void setRole(DpUserRole role) { this.role = role; }

    public Instant getAccountCreatedDatetime() { return accountCreatedDatetime; }

    public Instant getAccountModifiedDatetime() { return accountModifiedDatetime; }

    public Instant getLastPasswordReset() { return lastPasswordReset; }
    public void setLastPasswordReset(Instant lastPasswordReset) { this.lastPasswordReset = lastPasswordReset; }

    public Boolean getMfaActive() { return mfaActive; }
    public void setMfaActive(Boolean mfaActive) { this.mfaActive = mfaActive; }
}
