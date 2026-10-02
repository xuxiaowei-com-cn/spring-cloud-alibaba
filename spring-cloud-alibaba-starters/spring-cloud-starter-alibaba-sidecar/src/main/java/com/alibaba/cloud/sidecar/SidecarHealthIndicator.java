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
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * @author www.itmuch.com
 */
public class SidecarHealthIndicator extends AbstractHealthIndicator {

	private final SidecarProperties sidecarProperties;

	private final RestClient restClient;

	public SidecarHealthIndicator(SidecarProperties sidecarProperties,
			RestClient restClient) {
		this.sidecarProperties = sidecarProperties;
		this.restClient = restClient;
	}

	/**
	 * Creates an indicator backed by the given {@link RestTemplate}.
	 * <p>
	 * The {@code RestTemplate} is adapted with {@link RestClient#create(RestTemplate)} so
	 * that a project which has not migrated yet keeps exactly the configuration
	 * (interceptors, converters, request factory, error handler) it has always used.
	 * @param sidecarProperties the sidecar properties
	 * @param restTemplate the {@code RestTemplate} used to perform the health check
	 */
	public SidecarHealthIndicator(SidecarProperties sidecarProperties,
			RestTemplate restTemplate) {
		this(sidecarProperties, RestClient.create(restTemplate));
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		try {
			URI uri = this.sidecarProperties.getHealthCheckUrl();
			if (uri == null) {
				builder.up();
				return;
			}

			Map<String, Object> map = this.restClient.get()
					.uri(uri)
					.retrieve()
					.body(new ParameterizedTypeReference<Map<String, Object>>() {
					});

			if (map == null) {
				this.getWarning(builder);
				return;
			}
			Object status = map.get("status");
			if (status instanceof String strStatus) {
				builder.status(strStatus);
			}
			else {
				this.getWarning(builder);
			}
		}
		catch (Exception e) {
			builder.down().withDetail("error",
					Objects.toString(e.getMessage(), e.getClass().getName()));
		}
	}

	private void getWarning(Health.Builder builder) {
		builder.unknown().withDetail("warning", "no status field in response");
	}

}
