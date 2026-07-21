package com.balancify.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SupabaseAuthAdminClientContextTest {

    @Test
    void createsClientWithPropertiesConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(SupabaseAuthProperties.class, SupabaseAuthAdminClient.class);
            context.refresh();

            assertThat(context.getBean(SupabaseAuthAdminClient.class)).isNotNull();
        }
    }
}
