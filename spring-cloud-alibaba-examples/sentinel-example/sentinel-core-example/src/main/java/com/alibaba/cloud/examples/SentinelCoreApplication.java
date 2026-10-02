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

package com.alibaba.cloud.examples;

import java.util.Collections;

import com.alibaba.cloud.circuitbreaker.sentinel.SentinelCircuitBreakerFactory;
import com.alibaba.cloud.circuitbreaker.sentinel.SentinelConfigBuilder;
import com.alibaba.cloud.sentinel.annotation.SentinelRestClient;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * @author xiaojing
 */
@SpringBootApplication
public class SentinelCoreApplication {

	@Bean
	@SentinelRestClient(blockHandler = "handleException",
			blockHandlerClass = ExceptionUtil.class)
	public RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}

	@Bean
	public RestClient restClient(RestClient.Builder restClientBuilder) {
		return restClientBuilder.build();
	}

	@Bean
	public RestClient.Builder restClientBuilder2() {
		return RestClient.builder();
	}

	@Bean
	public RestClient restClient2(RestClient.Builder restClientBuilder2) {
		return restClientBuilder2.build();
	}

	@Bean
	public Converter myConverter() {
		return new JsonFlowRuleListConverter();
	}

	@Bean
	public Customizer<SentinelCircuitBreakerFactory> defaultConfig() {
		return factory -> {
			factory.configureDefault(
					id -> new SentinelConfigBuilder().resourceName(id)
							.rules(Collections.singletonList(new DegradeRule(id)
									.setGrade(RuleConstant.DEGRADE_GRADE_RT).setCount(100)
									.setTimeWindow(10)))
							.build());
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(SentinelCoreApplication.class, args);
	}

}
