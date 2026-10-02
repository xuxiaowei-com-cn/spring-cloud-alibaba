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

package com.alibaba.cloud.sidecar;

import java.net.URI;

import org.junit.jupiter.api.Test;

import org.springframework.boot.health.contributor.Status;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that Sidecar exposes {@code RestTemplate} and {@code RestClient} side by side and
 * that both can drive the health check, so that a project can migrate one client at a
 * time.
 *
 * @author <a href="https://github.com/xuxiaowei">xuxiaowei</a>
 */
public class SidecarRestClientTests {

	private static final URI UNREACHABLE_HEALTH_CHECK_URL = URI
			.create("http://127.0.0.1:1/health");

	@Test
	public void restTemplateAndRestClientBeansAreBothCreatedIndependently() {
		SidecarAutoConfiguration configuration = new SidecarAutoConfiguration();

		RestTemplate restTemplate = configuration.restTemplate();
		RestClient restClient = configuration.restClient();

		assertThat(restTemplate).isNotNull();
		assertThat(restClient).isNotNull();
	}

	@Test
	public void healthIndicatorWithRestClientReportsDownWhenEndpointIsUnreachable() {
		SidecarProperties properties = new SidecarProperties();
		properties.setHealthCheckUrl(UNREACHABLE_HEALTH_CHECK_URL);

		SidecarHealthIndicator indicator = new SidecarHealthIndicator(properties,
				RestClient.create());

		assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
	}

	@Test
	public void healthIndicatorWithRestTemplateBehavesTheSame() {
		SidecarProperties properties = new SidecarProperties();
		properties.setHealthCheckUrl(UNREACHABLE_HEALTH_CHECK_URL);

		SidecarHealthIndicator indicator = new SidecarHealthIndicator(properties,
				new RestTemplate());

		assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
	}

	@Test
	public void healthIndicatorIsUpWhenNoHealthCheckUrlIsConfigured() {
		SidecarHealthIndicator indicator = new SidecarHealthIndicator(
				new SidecarProperties(), RestClient.create());

		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
	}

}
