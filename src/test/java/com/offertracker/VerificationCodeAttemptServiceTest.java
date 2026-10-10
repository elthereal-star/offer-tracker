package com.offertracker;

import com.offertracker.entity.VerificationCode;
import com.offertracker.mapper.VerificationCodeMapper;
import com.offertracker.service.VerificationCodeAttemptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class VerificationCodeAttemptServiceTest {
    @Autowired VerificationCodeMapper codes;
    @Autowired VerificationCodeAttemptService attempts;
    @Autowired PlatformTransactionManager transactionManager;
    private Long codeId;

    @AfterEach
    void cleanUp() {
        if (codeId != null) codes.deleteById(codeId);
    }

    @Test
    void failedRegistrationAttemptSurvivesOuterTransactionRollback() {
        VerificationCode code = new VerificationCode();
        code.setPhone("+8613800000000");
        code.setPurpose("REGISTER");
        code.setCodeHash("test-hash");
        code.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        code.setAttempts(0);
        code.setCreatedAt(LocalDateTime.now());
        codes.insert(code);
        codeId = code.getId();

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            attempts.recordFailure(codeId);
            throw new IllegalStateException("simulate rejected registration");
        }));

        assertEquals(1, codes.selectById(codeId).getAttempts());
    }

    @Test
    void failedAttemptsAreAtomicallyCappedAtFive() {
        VerificationCode code = new VerificationCode();
        code.setPhone("+8613800000001");
        code.setPurpose("REGISTER");
        code.setCodeHash("test-hash");
        code.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        code.setAttempts(0);
        code.setCreatedAt(LocalDateTime.now());
        codes.insert(code);
        codeId = code.getId();

        for (int i = 0; i < 8; i++) attempts.recordFailure(codeId);

        assertEquals(5, codes.selectById(codeId).getAttempts());
    }
}
