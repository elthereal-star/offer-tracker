package com.offertracker;

import com.offertracker.config.ProductionAuthenticationGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionAuthenticationGuardTest {
    @Test
    void rejectsProductionConfigurationWithAuthenticationDisabled() {
        ProductionAuthenticationGuard guard = new ProductionAuthenticationGuard(false);

        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
    }

    @Test
    void acceptsProductionConfigurationWithAuthenticationEnabled() {
        ProductionAuthenticationGuard guard = new ProductionAuthenticationGuard(true);

        assertDoesNotThrow(guard::afterPropertiesSet);
    }
}
