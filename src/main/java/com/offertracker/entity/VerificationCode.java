package com.offertracker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("auth_verification_codes")
public class VerificationCode {
    @TableId(type = IdType.AUTO) private Long id;
    private String phone; private String purpose; private String codeHash;
    private LocalDateTime expiresAt; private LocalDateTime consumedAt;
    private Integer attempts; private LocalDateTime createdAt;
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public String getPhone() { return phone; } public void setPhone(String v) { phone = v; }
    public String getPurpose() { return purpose; } public void setPurpose(String v) { purpose = v; }
    public String getCodeHash() { return codeHash; } public void setCodeHash(String v) { codeHash = v; }
    public LocalDateTime getExpiresAt() { return expiresAt; } public void setExpiresAt(LocalDateTime v) { expiresAt = v; }
    public LocalDateTime getConsumedAt() { return consumedAt; } public void setConsumedAt(LocalDateTime v) { consumedAt = v; }
    public Integer getAttempts() { return attempts; } public void setAttempts(Integer v) { attempts = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}
