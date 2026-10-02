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

package com.alibaba.cloud.seata.rest;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.seata.core.context.RootContext;
import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link SeataRestClientBuilderBeanPostProcessor} registers the Seata XID
 * interceptor on every {@link RestClient.Builder}, so that a project can use
 * {@code RestClient} and {@code RestTemplate} side by side.
 *
 * @author <a href="https://github.com/xuxiaowei">xuxiaowei</a>
 */
public class SeataRestClientBuilderBeanPostProcessorTests {

	@Test
	public void restClientBuilderGetsExactlyOneSeataInterceptor() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				Config.class)) {
			RestClient.Builder restClientBuilder = context
					.getBean(RestClient.Builder.class);

			assertThat(interceptors(restClientBuilder)).hasSize(1)
					.first()
					.isInstanceOf(SeataRestTemplateInterceptor.class);
		}
	}

	@Test
	public void registeredInterceptorPropagatesSeataXid() throws Exception {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				Config.class)) {
			RestClient.Builder restClientBuilder = context
					.getBean(RestClient.Builder.class);
			ClientHttpRequestInterceptor interceptor = interceptors(restClientBuilder)
					.get(0);

			MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
					URI.create("http://localhost/test"));

			RootContext.bind("123456");
			try {
				ClientHttpResponse response = interceptor.intercept(request, new byte[0],
						(req, body) -> {
							assertThat(req.getHeaders().getFirst(RootContext.KEY_XID))
									.isEqualTo("123456");
							return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
						});

				assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			}
			finally {
				RootContext.unbind();
			}
		}
	}

	private List<ClientHttpRequestInterceptor> interceptors(
			RestClient.Builder restClientBuilder) {
		AtomicReference<List<ClientHttpRequestInterceptor>> reference = new AtomicReference<>();
		restClientBuilder.requestInterceptors(list -> reference.set(new ArrayList<>(list)));
		return reference.get();
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		static SeataRestClientBuilderBeanPostProcessor seataRestClientBuilderBeanPostProcessor(
				SeataRestTemplateInterceptor seataRestTemplateInterceptor) {
			return new SeataRestClientBuilderBeanPostProcessor(
					seataRestTemplateInterceptor);
		}

		@Bean
		static SeataRestTemplateInterceptor seataRestTemplateInterceptor() {
			return new SeataRestTemplateInterceptor();
		}

		@Bean
		RestClient.Builder restClientBuilder() {
			return RestClient.builder();
		}

	}

}
