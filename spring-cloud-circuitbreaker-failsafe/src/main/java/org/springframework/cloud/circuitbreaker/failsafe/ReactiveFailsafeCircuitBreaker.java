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

import java.util.Optional;
import java.util.function.Function;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.circuitbreaker.commons.Customizer;
import org.springframework.cloud.circuitbreaker.commons.ReactiveCircuitBreaker;
import org.springframework.util.Assert;

/**
 * @author Jakub Marchwicki
 */
public class ReactiveFailsafeCircuitBreaker implements ReactiveCircuitBreaker {

	private final String id;

	private final FailsafeConfigBuilder.FailsafeConfig config;

	private final Optional<Customizer<FailsafeExecutor>> failsafeCustomizer;

	public ReactiveFailsafeCircuitBreaker(String id,
		FailsafeConfigBuilder.FailsafeConfig config,
		Optional<Customizer<FailsafeExecutor>> failsafeCustomizer) {
		this.id = id;
		this.config = config;
		this.failsafeCustomizer = failsafeCustomizer;
	}

	@Override
	public <T> Mono<T> run(Mono<T> toRun, Function<Throwable, Mono<T>> fallback) {
		Assert.notNull(fallback, "Feedback function required");

		FailsafeExecutor<T> failsafeExecutor = Failsafe.with(config.getRetryPolicy(),
			config.getCircuitBreaker());
		failsafeCustomizer
			.ifPresent(customizer -> customizer.customize(failsafeExecutor));

		return toRun
			.flatMap(t -> Mono.fromFuture(failsafeExecutor.getAsync(() -> t)))
			.onErrorResume(fallback);
	}

	@Override
	public <T> Flux<T> run(Flux<T> toRun, Function<Throwable, Flux<T>> fallback) {
		Assert.notNull(fallback, "Feedback function required");

		FailsafeExecutor<T> failsafeExecutor = Failsafe.with(config.getRetryPolicy(),
			config.getCircuitBreaker());
		failsafeCustomizer
			.ifPresent(customizer -> customizer.customize(failsafeExecutor));

		return toRun
			.transform(flux -> Flux.fromStream(
				flux.toStream().map(t -> failsafeExecutor.get(() -> t))))
			.onErrorResume(fallback);
	}

}
