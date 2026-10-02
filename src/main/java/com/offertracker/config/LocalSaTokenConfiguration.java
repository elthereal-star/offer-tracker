package com.offertracker.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!production")
public class LocalSaTokenConfiguration {
    @Bean
    @Primary
    public SaTokenDao localSaTokenDao() {
        return new SaTokenDaoDefaultImpl();
    }
}
