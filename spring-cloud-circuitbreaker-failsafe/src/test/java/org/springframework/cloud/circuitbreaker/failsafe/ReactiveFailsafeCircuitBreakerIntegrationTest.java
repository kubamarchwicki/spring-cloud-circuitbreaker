/*
 * Copyright 2013-2018 the original author or authors.
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

import java.time.Duration;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import net.jodah.failsafe.RetryPolicy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.circuitbreaker.commons.Customizer;
import org.springframework.cloud.circuitbreaker.commons.ReactiveCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Jakub Marchwicki
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT,
	classes = ReactiveFailsafeCircuitBreakerIntegrationTest.Application.class)
@DirtiesContext
public class ReactiveFailsafeCircuitBreakerIntegrationTest {

	@LocalServerPort
	private int port = 0;

	@Autowired
	ReactiveFailsafeCircuitBreakerIntegrationTest.Application.DemoControllerService service;

	@Before
	public void setup() {
		service.setPort(port);
	}

	@Test
	public void normal_consumer() throws Exception {
		StepVerifier.create(service.normal()).expectNext("normal").verifyComplete();
	}

	@Test
	public void slow_consumer() throws Exception {
		StepVerifier.withVirtualTime(() -> service.slow()).expectSubscription()
			.expectNoEvent(Duration.ofSeconds(1)).expectNext("fallback")
			.expectComplete().verify();
	}

	@Test
	public void normal_flux_consumer() throws Exception {
		StepVerifier.create(service.normalFlux()).expectNext("normalflux")
			.verifyComplete();
	}

	@Test
	public void slow_flux_consumer() throws Exception {
		StepVerifier.create(service.slowFlux()).expectNext("fluxfallback")
			.verifyComplete();
	}

	@Configuration
	@EnableAutoConfiguration
	@RestController
	protected static class Application {

		@GetMapping("/slow")
		public Mono<String> slow() {
			return Mono.just("slow").delayElement(Duration.ofSeconds(3));
		}

		@GetMapping("/normal")
		public Mono<String> normal() {
			return Mono.just("normal");
		}

		@GetMapping("/slowflux")
		public Flux<String> slowFlux() {
			return Flux.just("slow", "flux").delayElements(Duration.ofSeconds(3));
		}

		@GetMapping("/normalflux")
		public Flux<String> normalFlux() {
			return Flux.just("normal", "flux");
		}

		@Bean
		public Customizer<ReactiveFailsafeCircuitBreakerFactory> factoryCustomizer() {
			return factory -> factory.configureDefault(
				id -> new FailsafeConfigBuilder(id)
					.retryPolicy(new RetryPolicy<>().withMaxAttempts(1))
					.circuitBreaker(new net.jodah.failsafe.CircuitBreaker<>()
						.handle(Exception.class).withFailureThreshold(1)
						.withTimeout(Duration.ofSeconds(2))
						.withDelay(Duration.ofMinutes(1)))
					.build());
		}

		@Service
		public static class DemoControllerService {

			private int port = 0;

			private ReactiveCircuitBreakerFactory cbFactory;
			private TcpClient timeoutClient = TcpClient.create()
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10)
				.doOnConnected(con -> con.addHandlerLast(new ReadTimeoutHandler(1))
					.addHandlerLast(new WriteTimeoutHandler(1)));

			DemoControllerService(ReactiveCircuitBreakerFactory cbFactory) {
				this.cbFactory = cbFactory;
			}

			public Mono<String> slow() {
				return WebClient.builder()
					.clientConnector(new ReactorClientHttpConnector(HttpClient.from(timeoutClient)))
					.baseUrl("http://localhost:" + port).build()
					.get().uri("/slow").retrieve().bodyToMono(String.class)
					.transform(it -> cbFactory.create("slow").run(it, t -> Mono.just("fallback")));
			}

			public Mono<String> normal() {
				return WebClient.builder().baseUrl("http://localhost:" + port).build()
					.get().uri("/normal").retrieve().bodyToMono(String.class)
					.transform(it -> cbFactory.create("normal").run(it, t -> Mono.just("fallback")));
			}

			public Flux<String> slowFlux() {
				return WebClient.builder()
					.clientConnector(new ReactorClientHttpConnector(HttpClient.from(timeoutClient)))
					.baseUrl("http://localhost:" + port).build()
					.get().uri("/slowflux").retrieve()
					.bodyToFlux(new ParameterizedTypeReference<String>() {
					}).transform(it -> cbFactory.create("slowflux").run(it, t -> Flux.just("fluxfallback")));
			}

			public Flux<String> normalFlux() {
				return WebClient.builder().baseUrl("http://localhost:" + port).build()
					.get().uri("/normalflux").retrieve().bodyToFlux(String.class)
					.transform(it -> cbFactory.create("normalflux").run(it, t -> Flux.just("fluxfallback")));
			}

			public void setPort(int port) {
				this.port = port;
			}

		}

	}

}
