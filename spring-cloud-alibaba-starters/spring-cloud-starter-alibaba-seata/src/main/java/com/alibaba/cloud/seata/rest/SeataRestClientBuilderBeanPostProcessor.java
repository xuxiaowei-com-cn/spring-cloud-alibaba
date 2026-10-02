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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.client.RestClient;

/**
 * {@link BeanPostProcessor} that registers the {@link SeataRestClientInterceptor} on
 * every {@link RestClient.Builder} so that the Seata XID is propagated by the
 * {@link RestClient} instances built from them.
 *
 * @author ZhangZhi
 */
public class SeataRestClientBuilderBeanPostProcessor implements BeanPostProcessor {

	private final SeataRestClientInterceptor seataRestClientInterceptor;

	public SeataRestClientBuilderBeanPostProcessor(
			SeataRestClientInterceptor seataRestClientInterceptor) {
		this.seataRestClientInterceptor = seataRestClientInterceptor;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof RestClient.Builder restClientBuilder) {
			restClientBuilder.requestInterceptor(this.seataRestClientInterceptor);
		}
		return bean;
	}

}
