package com.mangateca.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

@Import({ TestcontainersConfiguration.class, TestSecurityConfiguration.class })
@SpringBootTest
class ApiApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
	}

	@Test
	@DisplayName("FR-009: open-in-view desactivado")
	void openInViewIsDisabled() {
		assertThat(context.getBeansOfType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
	}

}
