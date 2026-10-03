package com.ridelink.account.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAdminBootstrapProfileTest {

    @Test
    void defaultProfileDoesNotRegisterLocalAdminBootstrap() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(LocalAdminBootstrap.class);
            context.refresh();

            assertThat(context.getBeansOfType(LocalAdminBootstrap.class)).isEmpty();
        }
    }
}
