package com.balancify.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A server or hosting bill, kept apart from the donation expense ledger.
 *
 * <p>These are usually paid on a member's personal card first, so they only affect the donation
 * account once {@link #reimbursedDate} is set - that is, once the account has paid the member back.
 */
@Entity
@Table(name = "ledger_server_costs")
public class LedgerServerCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "service_name", nullable = false, length = 50)
    private String serviceName;

    /** Billing period as {@code yyyy-MM}. */
    @Column(name = "billing_month", nullable = false, length = 7)
    private String billingMonth;

    @Column(name = "charged_date", nullable = false)
    private LocalDate chargedDate;

    /** Amount on the provider's invoice, when it bills in US dollars. */
    @Column(name = "usd_amount", precision = 10, scale = 2)
    private BigDecimal usdAmount;

    /** Amount actually charged to the card in won; unknown until the card statement arrives. */
    @Column(name = "krw_amount")
    private Long krwAmount;

    @Column(name = "paid_by", length = 100)
    private String paidBy;

    @Column(name = "reimbursed_date")
    private LocalDate reimbursedDate;

    @Column(length = 500)
    private String memo;

    @Column(name = "author_email", nullable = false, length = 320)
    private String authorEmail;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PrePersist
    @PreUpdate
    private void sync() {
        this.updatedAt = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getBillingMonth() {
        return billingMonth;
    }

    public void setBillingMonth(String billingMonth) {
        this.billingMonth = billingMonth;
    }

    public LocalDate getChargedDate() {
        return chargedDate;
    }

    public void setChargedDate(LocalDate chargedDate) {
        this.chargedDate = chargedDate;
    }

    public BigDecimal getUsdAmount() {
        return usdAmount;
    }

    public void setUsdAmount(BigDecimal usdAmount) {
        this.usdAmount = usdAmount;
    }

    public Long getKrwAmount() {
        return krwAmount;
    }

    public void setKrwAmount(Long krwAmount) {
        this.krwAmount = krwAmount;
    }

    public String getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(String paidBy) {
        this.paidBy = paidBy;
    }

    public LocalDate getReimbursedDate() {
        return reimbursedDate;
    }

    public void setReimbursedDate(LocalDate reimbursedDate) {
        this.reimbursedDate = reimbursedDate;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
