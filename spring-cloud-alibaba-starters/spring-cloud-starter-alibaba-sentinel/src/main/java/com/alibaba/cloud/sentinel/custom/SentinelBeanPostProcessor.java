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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.cloud.sentinel.SentinelConstants;
import com.alibaba.cloud.sentinel.annotation.SentinelRestClient;
import com.alibaba.cloud.sentinel.annotation.SentinelRestTemplate;
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
import org.springframework.web.client.RestTemplate;

/**
 * PostProcessor handle @SentinelRestTemplate and @SentinelRestClient Annotation, add
 * interceptor for RestTemplate and RestClient.
 * <p>
 * {@code @SentinelRestTemplate} is declared on a {@link RestTemplate} bean and
 * {@code @SentinelRestClient} on a {@link RestClient.Builder} bean, so both clients can
 * be protected by Sentinel at the same time.
 *
 * @author <a href="mailto:fangjian0423@gmail.com">Jim</a>
 * @see SentinelRestTemplate
 * @see SentinelRestClient
 * @see SentinelProtectInterceptor
 */
public class SentinelBeanPostProcessor implements MergedBeanDefinitionPostProcessor {

	private static final Logger log = LoggerFactory
			.getLogger(SentinelBeanPostProcessor.class);

	private final ApplicationContext applicationContext;

	private final ConcurrentHashMap<String, InterceptorMetadata> cache = new ConcurrentHashMap<>();

	public SentinelBeanPostProcessor(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition,
			Class<?> beanType, String beanName) {
		if (beanName == null) {
			return;
		}

		// Fixes #3329: Support custom RestTemplate
		if (RestTemplate.class.isAssignableFrom(beanType)) {
			SentinelRestTemplate sentinelRestTemplate = this
					.getAnnotationFromBeanDefinition(beanDefinition, SentinelRestTemplate.class);
			if (sentinelRestTemplate != null) {
				// check class and method validation
				InterceptorMetadata metadata = InterceptorMetadata
						.ofRestTemplate(sentinelRestTemplate);
				checkInterceptorMetadata(metadata, beanName);
				cache.put(beanName, metadata);
			}
			return;
		}

		// Support custom RestClient.Builder
		if (RestClient.Builder.class.isAssignableFrom(beanType)) {
			SentinelRestClient sentinelRestClient = this
					.getAnnotationFromBeanDefinition(beanDefinition, SentinelRestClient.class);
			if (sentinelRestClient != null) {
				// check class and method validation
				InterceptorMetadata metadata = InterceptorMetadata
						.ofRestClient(sentinelRestClient);
				checkInterceptorMetadata(metadata, beanName);
				cache.put(beanName, metadata);
			}
		}
	}

	private <A extends Annotation> @Nullable A getAnnotationFromBeanDefinition(
			RootBeanDefinition beanDefinition, Class<A> annotationType) {
		@Nullable A annotation = null;
		if (beanDefinition.getSource() instanceof StandardMethodMetadata sentinelSource) {
			annotation = sentinelSource.getIntrospectedMethod()
					.getAnnotation(annotationType);
		}

		if (annotation == null && beanDefinition.getResolvedFactoryMethod() != null) {
			annotation = beanDefinition.getResolvedFactoryMethod()
					.getAnnotation(annotationType);
		}

		return annotation;
	}

	private void checkInterceptorMetadata(InterceptorMetadata metadata, String beanName) {
		checkBlock4RestClient(metadata.blockHandlerClass(), metadata.blockHandler(),
				beanName, SentinelConstants.BLOCK_TYPE);
		checkBlock4RestClient(metadata.fallbackClass(), metadata.fallback(), beanName,
				SentinelConstants.FALLBACK_TYPE);
		checkBlock4RestClient(metadata.urlCleanerClass(), metadata.urlCleaner(), beanName,
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
		if (beanName == null) {
			return bean;
		}
		InterceptorMetadata metadata = this.cache.get(beanName);
		if (metadata == null) {
			return bean;
		}

		String interceptorBeanName = buildInterceptorBeanName(metadata, bean);

		if (metadata.restTemplate()) {
			// add interceptor for each RestTemplate with @SentinelRestTemplate
			// annotation
			RestTemplate restTemplate = (RestTemplate) bean;
			registerBean(interceptorBeanName, metadata, restTemplate);
			SentinelProtectInterceptor sentinelProtectInterceptor = applicationContext
					.getBean(interceptorBeanName, SentinelProtectInterceptor.class);
			restTemplate.getInterceptors().add(0, sentinelProtectInterceptor);
		}
		else {
			// add interceptor for each RestClient.Builder with @SentinelRestClient
			// annotation
			RestClient.Builder restClientBuilder = (RestClient.Builder) bean;
			registerBean(interceptorBeanName, metadata, null);
			SentinelProtectInterceptor sentinelProtectInterceptor = applicationContext
					.getBean(interceptorBeanName, SentinelProtectInterceptor.class);
			// Insert first so that Sentinel sees the original (for example
			// load-balanced) URI before any other interceptor rewrites it.
			restClientBuilder.requestInterceptors(
					interceptors -> interceptors.add(0, sentinelProtectInterceptor));
		}
		return bean;
	}

	private String buildInterceptorBeanName(InterceptorMetadata metadata, Object bean) {
		return StringUtils.uncapitalize(
				SentinelProtectInterceptor.class.getSimpleName()) + "_"
				+ metadata.blockHandlerClass().getSimpleName() + metadata.blockHandler()
				+ "_" + metadata.fallbackClass().getSimpleName() + metadata.fallback()
				+ "_" + metadata.urlCleanerClass().getSimpleName()
				+ metadata.urlCleaner() + "@" + bean;
	}

	private void registerBean(String interceptorBeanName, InterceptorMetadata metadata,
			@Nullable RestTemplate restTemplate) {
		// register SentinelProtectInterceptor bean
		DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext
				.getAutowireCapableBeanFactory();
		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder
				.genericBeanDefinition(SentinelProtectInterceptor.class);
		beanDefinitionBuilder.addConstructorArgValue(metadata.annotation());
		if (restTemplate != null) {
			beanDefinitionBuilder.addConstructorArgValue(restTemplate);
		}
		BeanDefinition interceptorBeanDefinition = beanDefinitionBuilder
				.getRawBeanDefinition();
		beanFactory.registerBeanDefinition(interceptorBeanName,
				interceptorBeanDefinition);
	}

	private record InterceptorMetadata(Annotation annotation, Class<?> blockHandlerClass,
			String blockHandler, Class<?> fallbackClass, String fallback,
			Class<?> urlCleanerClass, String urlCleaner, boolean restTemplate) {

		static InterceptorMetadata ofRestTemplate(SentinelRestTemplate annotation) {
			return new InterceptorMetadata(annotation, annotation.blockHandlerClass(),
					annotation.blockHandler(), annotation.fallbackClass(),
					annotation.fallback(), annotation.urlCleanerClass(),
					annotation.urlCleaner(), true);
		}

		static InterceptorMetadata ofRestClient(SentinelRestClient annotation) {
			return new InterceptorMetadata(annotation, annotation.blockHandlerClass(),
					annotation.blockHandler(), annotation.fallbackClass(),
					annotation.fallback(), annotation.urlCleanerClass(),
					annotation.urlCleaner(), false);
		}

	}

}
