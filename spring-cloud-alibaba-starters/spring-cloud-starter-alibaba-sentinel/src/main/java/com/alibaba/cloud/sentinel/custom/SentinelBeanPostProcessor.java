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

package com.alibaba.cloud.sentinel.custom;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.cloud.sentinel.SentinelConstants;
import com.alibaba.cloud.sentinel.annotation.SentinelRestClient;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * PostProcessor handle @SentinelRestClient Annotation, add interceptor for RestClient.
 *
 * @author <a href="mailto:fangjian0423@gmail.com">Jim</a>
 * @see SentinelRestClient
 * @see SentinelProtectInterceptor
 */
public class SentinelBeanPostProcessor implements MergedBeanDefinitionPostProcessor {

	private static final Logger log = LoggerFactory
			.getLogger(SentinelBeanPostProcessor.class);

	private final ApplicationContext applicationContext;

	public SentinelBeanPostProcessor(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	private ConcurrentHashMap<String, SentinelRestClient> cache = new ConcurrentHashMap<>();

	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition,
			Class<?> beanType, String beanName) {
		// Fixes #3329: Support custom RestClient.Builder
		if (beanName == null || !RestClient.Builder.class.isAssignableFrom(beanType)) {
			return;
		}

		SentinelRestClient sentinelRestClient = this.getSentinelRestClientFromBeanDefinition(beanDefinition);
		if (sentinelRestClient != null) {
			// check class and method validation
			checkSentinelRestClient(sentinelRestClient, beanName);
			cache.put(beanName, sentinelRestClient);
		}
	}

	private @Nullable SentinelRestClient getSentinelRestClientFromBeanDefinition(RootBeanDefinition beanDefinition) {
		@Nullable SentinelRestClient sentinelRestClient = null;
		if (beanDefinition.getSource() instanceof StandardMethodMetadata sentinelSource) {
			sentinelRestClient = sentinelSource.getIntrospectedMethod().getAnnotation(SentinelRestClient.class);
		}

		if (sentinelRestClient == null && beanDefinition.getResolvedFactoryMethod() != null) {
			sentinelRestClient = beanDefinition.getResolvedFactoryMethod().getAnnotation(SentinelRestClient.class);
		}

		return sentinelRestClient;
	}

	private void checkSentinelRestClient(SentinelRestClient sentinelRestClient,
			String beanName) {
		checkBlock4RestClient(sentinelRestClient.blockHandlerClass(),
				sentinelRestClient.blockHandler(), beanName,
				SentinelConstants.BLOCK_TYPE);
		checkBlock4RestClient(sentinelRestClient.fallbackClass(),
				sentinelRestClient.fallback(), beanName,
				SentinelConstants.FALLBACK_TYPE);
		checkBlock4RestClient(sentinelRestClient.urlCleanerClass(),
				sentinelRestClient.urlCleaner(), beanName,
				SentinelConstants.URLCLEANER_TYPE);
	}

	private void checkBlock4RestClient(Class<?> blockClass, String blockMethod,
			String beanName, String type) {
		if (blockClass == void.class && !StringUtils.hasLength(blockMethod)) {
			return;
		}
		if (blockClass != void.class && !StringUtils.hasLength(blockMethod)) {
			log.error(
					"{} class attribute exists but {} method attribute is not exists in bean[{}]",
					type, type, beanName);
			throw new IllegalArgumentException(type + " class attribute exists but "
					+ type + " method attribute is not exists in bean[" + beanName + "]");
		}
		else if (blockClass == void.class && StringUtils.hasLength(blockMethod)) {
			log.error(
					"{} method attribute exists but {} class attribute is not exists in bean[{}]",
					type, type, beanName);
			throw new IllegalArgumentException(type + " method attribute exists but "
					+ type + " class attribute is not exists in bean[" + beanName + "]");
		}
		Class[] args;
		if (type.equals(SentinelConstants.URLCLEANER_TYPE)) {
			args = new Class[] {String.class};
		}
		else {
			args = new Class[] {HttpRequest.class, byte[].class,
					ClientHttpRequestExecution.class, BlockException.class};
		}
		String argsStr = Arrays.toString(
				Arrays.stream(args).map(clazz -> clazz.getSimpleName()).toArray());
		Method foundMethod = ClassUtils.getStaticMethod(blockClass, blockMethod, args);
		if (foundMethod == null) {
			log.error(
					"{} static method can not be found in bean[{}]. The right method signature is {}#{}{}, please check your class name, method name and arguments",
					type, beanName, blockClass.getName(), blockMethod, argsStr);
			throw new IllegalArgumentException(type
					+ " static method can not be found in bean[" + beanName
					+ "]. The right method signature is " + blockClass.getName() + "#"
					+ blockMethod + argsStr
					+ ", please check your class name, method name and arguments");
		}

		Class<?> standardReturnType;
		if (type.equals(SentinelConstants.URLCLEANER_TYPE)) {
			standardReturnType = String.class;
		}
		else {
			standardReturnType = ClientHttpResponse.class;
		}

		if (!standardReturnType.isAssignableFrom(foundMethod.getReturnType())) {
			log.error("{} method return value in bean[{}] is not {}: {}#{}{}", type,
					beanName, standardReturnType.getName(), blockClass.getName(),
					blockMethod, argsStr);
			throw new IllegalArgumentException(type + " method return value in bean["
					+ beanName + "] is not " + standardReturnType.getName() + ": "
					+ blockClass.getName() + "#" + blockMethod + argsStr);
		}
		if (type.equals(SentinelConstants.BLOCK_TYPE)) {
			BlockClassRegistry.updateBlockHandlerFor(blockClass, blockMethod,
					foundMethod);
		}
		else if (type.equals(SentinelConstants.FALLBACK_TYPE)) {
			BlockClassRegistry.updateFallbackFor(blockClass, blockMethod, foundMethod);
		}
		else {
			BlockClassRegistry.updateUrlCleanerFor(blockClass, blockMethod, foundMethod);
		}
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		if (beanName != null && cache.containsKey(beanName)) {
			// add interceptor for each RestClient.Builder with @SentinelRestClient
			// annotation
			StringBuilder interceptorBeanNamePrefix = new StringBuilder();
			SentinelRestClient sentinelRestClient = cache.get(beanName);
			interceptorBeanNamePrefix
					.append(StringUtils.uncapitalize(
							SentinelProtectInterceptor.class.getSimpleName()))
					.append("_")
					.append(sentinelRestClient.blockHandlerClass().getSimpleName())
					.append(sentinelRestClient.blockHandler()).append("_")
					.append(sentinelRestClient.fallbackClass().getSimpleName())
					.append(sentinelRestClient.fallback()).append("_")
					.append(sentinelRestClient.urlCleanerClass().getSimpleName())
					.append(sentinelRestClient.urlCleaner());
			RestClient.Builder restClientBuilder = (RestClient.Builder) bean;
			String interceptorBeanName = interceptorBeanNamePrefix + "@"
					+ bean.toString();
			registerBean(interceptorBeanName, sentinelRestClient);
			SentinelProtectInterceptor sentinelProtectInterceptor = applicationContext
					.getBean(interceptorBeanName, SentinelProtectInterceptor.class);
			// Insert first so that Sentinel sees the original (for example
			// load-balanced) URI before any other interceptor rewrites it.
			restClientBuilder.requestInterceptors(
					interceptors -> interceptors.add(0, sentinelProtectInterceptor));
		}
		return bean;
	}

	private void registerBean(String interceptorBeanName,
			SentinelRestClient sentinelRestClient) {
		// register SentinelProtectInterceptor bean
		DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext
				.getAutowireCapableBeanFactory();
		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder
				.genericBeanDefinition(SentinelProtectInterceptor.class);
		beanDefinitionBuilder.addConstructorArgValue(sentinelRestClient);
		BeanDefinition interceptorBeanDefinition = beanDefinitionBuilder
				.getRawBeanDefinition();
		beanFactory.registerBeanDefinition(interceptorBeanName,
				interceptorBeanDefinition);
	}

}
