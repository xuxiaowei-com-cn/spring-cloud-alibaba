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

package com.alibaba.cloud.examples.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * @author raozihao
 * @author <a href="mailto:zihaorao@gmail.com">Steve</a>
 */
@RestController
public class TestController {

	@Autowired
	RestClient restClient;

	@GetMapping("/exp")
	public String exp() {
		return restClient.get().uri("https://httpbin.org/status/500").retrieve()
				.body(String.class);
	}

	@GetMapping("/rt")
	public String rt() {
		return restClient.get().uri("https://httpbin.org/delay/3").retrieve()
				.body(String.class);
	}

	@GetMapping("/get")
	public String get() {
		return restClient.get().uri("https://httpbin.org/get").retrieve()
				.body(String.class);
	}

}
