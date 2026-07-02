package com.rana.cardlens.model;

import jakarta.persistence.*;

@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName;

    @Column(name = "card_label", nullable = false, length = 50)
    private String cardLabel;

    @Column(name = "last4", nullable = false, length = 4)
    private String last4;

    @Column(name = "sender_email", nullable = false, length = 100)
    private String senderEmail;

    @Column(name = "subject_pattern", length = 200)
    private String subjectPattern;

    // Points to an ENV VAR name, never the password formula itself.
    @Column(name = "password_template_key", nullable = false, length = 50)
    private String passwordTemplateKey;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getCardLabel() { return cardLabel; }
    public void setCardLabel(String cardLabel) { this.cardLabel = cardLabel; }

    public String getLast4() { return last4; }
    public void setLast4(String last4) { this.last4 = last4; }

    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }

    public String getSubjectPattern() { return subjectPattern; }
    public void setSubjectPattern(String subjectPattern) { this.subjectPattern = subjectPattern; }

    public String getPasswordTemplateKey() { return passwordTemplateKey; }
    public void setPasswordTemplateKey(String passwordTemplateKey) { this.passwordTemplateKey = passwordTemplateKey; }
}
