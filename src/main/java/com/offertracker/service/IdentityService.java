package com.offertracker.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.offertracker.common.BusinessException;
import com.offertracker.dto.*;
import com.offertracker.entity.*;
import com.offertracker.mapper.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service public class IdentityService {
 private final UserMapper users; private final AuthRefreshSessionMapper sessions; private final VerificationCodeMapper codes; private final VerificationCodeAttemptService codeAttempts; private final SmsRequestLimiter smsRequestLimiter; private final SmsCodeSender sms; private final JwtTokenService jwt; private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12); private final SecureRandom random=new SecureRandom();
 public IdentityService(UserMapper u,AuthRefreshSessionMapper s,VerificationCodeMapper c,VerificationCodeAttemptService a,SmsRequestLimiter l,SmsCodeSender sms,JwtTokenService j){users=u;sessions=s;codes=c;codeAttempts=a;smsRequestLimiter=l;this.sms=sms;jwt=j;}
 @Transactional public void sendRegistrationCode(String phone){phone=phone.trim();smsRequestLimiter.checkAllowed(phone); if(users.selectCount(new LambdaQueryWrapper<User>().eq(User::getPhone,phone))>0)throw new BusinessException(409,"手机号已注册"); String code="%06d".formatted(random.nextInt(1_000_000)); VerificationCode v=new VerificationCode(); LocalDateTime now=LocalDateTime.now(); v.setPhone(phone);v.setPurpose("REGISTER");v.setCodeHash(hash(code));v.setExpiresAt(now.plusMinutes(10));v.setAttempts(0);v.setCreatedAt(now);codes.insert(v);sms.send(phone,code);}
 @Transactional public AuthTokenResponse register(RegisterRequest r){String phone=r.phone().trim(); if(users.selectCount(new LambdaQueryWrapper<User>().eq(User::getPhone,phone))>0)throw new BusinessException(409,"手机号已注册"); VerificationCode v=codes.selectOne(new LambdaQueryWrapper<VerificationCode>().eq(VerificationCode::getPhone,phone).eq(VerificationCode::getPurpose,"REGISTER").isNull(VerificationCode::getConsumedAt).orderByDesc(VerificationCode::getCreatedAt).last("LIMIT 1")); if(v==null||v.getExpiresAt().isBefore(LocalDateTime.now())||v.getAttempts()>=5)throw new BusinessException(401,"验证码无效或已过期"); if(!MessageDigest.isEqual(v.getCodeHash().getBytes(StandardCharsets.US_ASCII),hash(r.code()).getBytes(StandardCharsets.US_ASCII))){codeAttempts.recordFailure(v.getId());throw new BusinessException(401,"验证码无效或已过期");}v.setConsumedAt(LocalDateTime.now());codes.updateById(v);User u=new User();u.setPhone(phone);u.setPasswordHash(passwords.encode(r.password()));u.setRole("USER");u.setStatus("ACTIVE");u.setCreatedAt(LocalDateTime.now());u.setUpdatedAt(LocalDateTime.now());users.insert(u);return issue(u,r.deviceLabel());}
 @Transactional public AuthTokenResponse login(LoginRequest r){User u=users.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone,r.phone().trim()));if(u==null||!passwords.matches(r.password(),u.getPasswordHash()))throw new BusinessException(401,"手机号或密码错误");return issue(u,r.deviceLabel());}
 @Transactional
 public AuthTokenResponse refresh(RefreshTokenRequest request) {
     AuthRefreshSession session = sessions.selectOne(new LambdaQueryWrapper<AuthRefreshSession>()
             .eq(AuthRefreshSession::getTokenHash, hash(request.refreshToken())));
     LocalDateTime now = LocalDateTime.now();
     if (session == null || session.getRevokedAt() != null || session.getExpiresAt().isBefore(now)) {
         throw new BusinessException(401, "刷新令牌无效或已过期");
     }

     User user = users.selectById(session.getUserId());
     if (user == null || !"ACTIVE".equals(user.getStatus())) {
         throw new BusinessException(401, "账户当前不可用");
     }

     int consumed = sessions.update(null, new UpdateWrapper<AuthRefreshSession>()
             .eq("id", session.getId())
             .isNull("revoked_at")
             .gt("expires_at", now)
             .set("revoked_at", now)
             .set("last_used_at", now));
     if (consumed != 1) throw new BusinessException(401, "刷新令牌无效或已过期");

     return issue(user, session.getDeviceLabel());
 }
 private AuthTokenResponse issue(User u,String device){byte[] b=new byte[32];random.nextBytes(b);String refresh=Base64.getUrlEncoder().withoutPadding().encodeToString(b);AuthRefreshSession s=new AuthRefreshSession();s.setUserId(u.getId());s.setTokenHash(hash(refresh));s.setDeviceLabel(device);s.setCreatedAt(LocalDateTime.now());s.setLastUsedAt(LocalDateTime.now());s.setExpiresAt(LocalDateTime.now().plusDays(30));sessions.insert(s);return new AuthTokenResponse("Bearer",jwt.createAccessToken(u.getId(),u.getRole()),refresh,JwtTokenService.ACCESS_TOKEN_SECONDS);}
 @Transactional public void logout(RefreshTokenRequest r){AuthRefreshSession s=sessions.selectOne(new LambdaQueryWrapper<AuthRefreshSession>().eq(AuthRefreshSession::getTokenHash,hash(r.refreshToken())));if(s!=null){s.setRevokedAt(LocalDateTime.now());sessions.updateById(s);}}
 private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
