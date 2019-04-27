/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.circuitbreaker.failsafe;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import net.jodah.failsafe.Failsafe;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.circuitbreaker.commons.CircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.commons.Customizer;
import org.springframework.cloud.circuitbreaker.commons.ReactiveCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Jakub Marchwicki
 */
@Configuration
@ConditionalOnClass(Failsafe.class)
public class FailsafeAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(CircuitBreakerFactory.class)
	public CircuitBreakerFactory failsafeCircuitBreakerFactory() {
		return new FailsafeCircuitBreakerFactory();
	}

	@Bean
	@ConditionalOnClass(
		name = { "reactor.core.publisher.Mono", "reactor.core.publisher.Flux" })
	@ConditionalOnMissingBean(ReactiveCircuitBreakerFactory.class)
	public ReactiveCircuitBreakerFactory reactiveFailsafeCircuitBreakerFactory() {
		return new ReactiveFailsafeCircuitBreakerFactory();
	}

	@Configuration
	public static class FailsafeCustomizerConfiguration {

		@Autowired(required = false)
		private List<Customizer<FailsafeCircuitBreakerFactory>> customizers = new ArrayList<>();

		@Autowired(required = false)
		private FailsafeCircuitBreakerFactory factory;

		@PostConstruct
		public void init() {
			customizers.forEach(customizer -> customizer.customize(factory));
		}

	}

	@Configuration
	public static class ReactiveFailsafeCustomizerConfiguration {

		@Autowired(required = false)
		private List<Customizer<ReactiveFailsafeCircuitBreakerFactory>> customizers = new ArrayList<>();

		@Autowired(required = false)
		private ReactiveFailsafeCircuitBreakerFactory factory;

		@PostConstruct
		public void init() {
			customizers.forEach(customizer -> customizer.customize(factory));
		}

	}

}
