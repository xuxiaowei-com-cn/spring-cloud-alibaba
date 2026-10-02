/*
 * Copyright 2013-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.sentinel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.alibaba.cloud.sentinel.annotation.SentinelRestClient;
import com.alibaba.cloud.sentinel.annotation.SentinelRestTemplate;
import com.alibaba.cloud.sentinel.custom.SentinelBeanPostProcessor;
import com.alibaba.cloud.sentinel.custom.SentinelProtectInterceptor;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that Sentinel protects {@code RestTemplate} and {@code RestClient} side by side,
 * so that a project can migrate one client at a time.
 *
 * @author <a href="mailto:fangjian0423@gmail.com">Jim</a>
 */
public class SentinelRestTemplateAndRestClientTests {

	private static final String URL = "http://localhost:9999/flow";

	private static final String DEFAULT_BLOCK_BODY = "RestTemplate request block by sentinel";

	@AfterEach
	public void clearRules() {
		FlowRuleManager.loadRules(Collections.emptyList());
	}

	private void blockAll(String resource) {
		FlowRule rule = new FlowRule();
		rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
		rule.setCount(0);
		rule.setResource(resource);
		FlowRuleManager.loadRules(Collections.singletonList(rule));
	}

	@Test
	public void restTemplateAndRestClientAreBothProtected() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				Config.class)) {
			RestTemplate restTemplate = context.getBean(RestTemplate.class);
			assertThat(restTemplate.getInterceptors()).hasSize(1)
					.first()
					.isInstanceOf(SentinelProtectInterceptor.class);

			RestClient.Builder restClientBuilder = context.getBean("restClientBuilder",
					RestClient.Builder.class);
			assertThat(interceptors(restClientBuilder)).hasSize(1)
					.first()
					.isInstanceOf(SentinelProtectInterceptor.class);

			assertThat(context.getBeansOfType(SentinelProtectInterceptor.class))
					.hasSize(2);
		}
	}

	@Test
	public void blockedRestTemplateCallReturnsSentinelResponse() {
		blockAll("GET:" + URL);
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				Config.class)) {
			ResponseEntity<String> response = context.getBean(RestTemplate.class)
					.getForEntity(URL, String.class);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isEqualTo(DEFAULT_BLOCK_BODY);
		}
	}

	@Test
	public void blockedRestClientCallReturnsSentinelResponse() {
		blockAll("GET:" + URL);
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				Config.class)) {
			ResponseEntity<String> response = context.getBean("restClient",
					RestClient.class)
					.get()
					.uri(URL)
					.retrieve()
					.toEntity(String.class);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isEqualTo(DEFAULT_BLOCK_BODY);
		}
	}

	@Test
	public void fallbackWithoutClassIsRejectedForRestClient() {
		assertThatThrownBy(() -> new AnnotationConfigApplicationContext(
				InvalidRestClientConfig.class))
				.isInstanceOf(BeanCreationException.class);
	}

	@Test
	public void fallbackWithoutClassIsRejectedForRestTemplate() {
		assertThatThrownBy(() -> new AnnotationConfigApplicationContext(
				InvalidRestTemplateConfig.class))
				.isInstanceOf(BeanCreationException.class);
	}

	private List<ClientHttpRequestInterceptor> interceptors(
			RestClient.Builder restClientBuilder) {
		List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
		restClientBuilder.requestInterceptors(interceptors::addAll);
		return interceptors;
	}

	@Configuration
	static class Config {

		@Bean
		SentinelBeanPostProcessor sentinelBeanPostProcessor(
				ApplicationContext applicationContext) {
			return new SentinelBeanPostProcessor(applicationContext);
		}

		@Bean
		@SentinelRestTemplate
		RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean
		@SentinelRestClient
		RestClient.Builder restClientBuilder() {
			return RestClient.builder();
		}

		@Bean
		RestClient restClient(RestClient.Builder restClientBuilder) {
			return restClientBuilder.build();
		}

	}

	@Configuration
	static class InvalidRestClientConfig {

		@Bean
		SentinelBeanPostProcessor sentinelBeanPostProcessor(
				ApplicationContext applicationContext) {
			return new SentinelBeanPostProcessor(applicationContext);
		}

		@Bean
		@SentinelRestClient(fallback = "fbk")
		RestClient.Builder restClientBuilder() {
			return RestClient.builder();
		}

	}

	@Configuration
	static class InvalidRestTemplateConfig {

		@Bean
		SentinelBeanPostProcessor sentinelBeanPostProcessor(
				ApplicationContext applicationContext) {
			return new SentinelBeanPostProcessor(applicationContext);
		}

		@Bean
		@SentinelRestTemplate(fallback = "fbk")
		RestTemplate restTemplate() {
			return new RestTemplate();
		}

	}

}
