package com.offertracker;

import com.offertracker.common.BusinessException;
import com.offertracker.common.CurrentUser;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.dto.CreateCompanyRequest;
import com.offertracker.entity.User;
import com.offertracker.mapper.UserMapper;
import com.offertracker.service.CompanyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class CompanyOwnershipTest {
    @Autowired CompanyService companies;
    @Autowired UserMapper users;

    @AfterEach
    void clearUserContext() { CurrentUserContext.clear(); }

    @Test
    void duplicateCheckIsCaseInsensitiveAndScopedToTheOwner() {
        User firstUser = createUser("+8613800011201");
        CurrentUserContext.set(new CurrentUser(firstUser.getId(), "USER"));
        companies.create(new CreateCompanyRequest("Example Corp", null, null));

        BusinessException sameOwner = assertThrows(BusinessException.class,
                () -> companies.create(new CreateCompanyRequest(" example corp ", null, null)));
        assertEquals(409, sameOwner.getCode());

        User secondUser = createUser("+8613800011202");
        CurrentUserContext.set(new CurrentUser(secondUser.getId(), "USER"));
        companies.create(new CreateCompanyRequest("EXAMPLE CORP", null, null));
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setPasswordHash("unused-test-hash");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        users.insert(user);
        return user;
    }
}
