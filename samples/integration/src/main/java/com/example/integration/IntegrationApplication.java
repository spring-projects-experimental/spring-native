package com.example.integration;

import java.time.Duration;
import java.util.Calendar;
import java.util.Date;

import javax.sql.DataSource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.interceptor.WireTap;
import org.springframework.integration.config.EnableIntegrationManagement;
import org.springframework.integration.config.EnableMessageHistory;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.integration.config.IntegrationConverter;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlowDefinition;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.integration.http.config.EnableIntegrationGraphController;
import org.springframework.integration.jdbc.store.JdbcChannelMessageStore;
import org.springframework.integration.jdbc.store.channel.H2ChannelMessageStoreQueryProvider;
import org.springframework.integration.redis.store.RedisChannelMessageStore;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.support.json.JacksonJsonUtils;
import org.springframework.integration.webflux.dsl.WebFlux;
import org.springframework.messaging.MessageHandler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.web.reactive.function.client.WebClient;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@SpringBootApplication
@EnableMessageHistory("dateChannel")
@EnableIntegrationManagement
@EnableIntegrationGraphController("/integration-graph")
public class IntegrationApplication {

	public static void main(String[] args) throws InterruptedException {
		ApplicationContext context = SpringApplication.run(IntegrationApplication.class, args);

		WebClient webClient =
				context.getBean(WebClient.Builder.class)
						.baseUrl("http://localhost:8080")
						.build();

		Thread.sleep(1000);

		System.out.println("Starting 'dateSourceEndpoint'...");

		webClient
				.get()
				.uri("control-bus/dateSourceEndpoint")
				.retrieve()
				.toBodilessEntity()
				.block(Duration.ofSeconds(10));

		Thread.sleep(1000);

		System.out.println("Obtaining integration graph...");

		String integrationGraph =
				webClient
						.get()
						.uri("integration-graph")
						.accept(MediaType.APPLICATION_JSON)
						.retrieve()
						.bodyToMono(String.class)
						.block(Duration.ofSeconds(10));

		System.out.println("CURRENT INTEGRATION GRAPH:\n" + integrationGraph);
	}

	@Bean
	MeterRegistry simpleMeterRegistry() {
		return new SimpleMeterRegistry();
	}

	@Bean
	JdbcChannelMessageStore jdbcChannelMessageStore(DataSource dataSource) {
		JdbcChannelMessageStore jdbcChannelMessageStore = new JdbcChannelMessageStore(dataSource);
		jdbcChannelMessageStore.setChannelMessageStoreQueryProvider(new H2ChannelMessageStoreQueryProvider());
		return jdbcChannelMessageStore;
	}

	@Bean
	RedisChannelMessageStore redisChannelMessageStore(RedisConnectionFactory connectionFactory) {
		RedisChannelMessageStore redisChannelMessageStore = new RedisChannelMessageStore(connectionFactory);
		redisChannelMessageStore.setValueSerializer(
				new GenericJackson2JsonRedisSerializer(JacksonJsonUtils.messagingAwareMapper()));
		return redisChannelMessageStore;
	}

	@Bean(PollerMetadata.DEFAULT_POLLER)
	PollerMetadata defaultPoller() {
		PollerMetadata pollerMetadata = new PollerMetadata();
		pollerMetadata.setTrigger(new PeriodicTrigger(10));
		return pollerMetadata;
	}

	@Bean
	IntegrationFlow printFormattedSecondsFlow(JdbcChannelMessageStore jdbcChannelMessageStore,
			RedisChannelMessageStore redisChannelMessageStore) {
		return IntegrationFlows
				.fromSupplier(Date::new,
						e -> e.id("dateSourceEndpoint")
								.poller(p -> p.fixedDelay(1000, 1000)))
				.channel(c -> c.queue("dateChannel", jdbcChannelMessageStore, "dateChannelGroup"))
				.gateway(subflow -> subflow.convert(Integer.class,
						e -> e.advice(new RequestHandlerRetryAdvice())))
				.channel(c -> c.queue(redisChannelMessageStore, "secondsChannelGroup"))
				.handle(m -> System.out.println("Current seconds: " + m.getPayload()))
				.get();
	}

	@Bean
	@GlobalChannelInterceptor(patterns = "dateChannel")
	WireTap loggingWireTap() {
		return new WireTap("loggingChannel");
	}

	@Bean
	@ServiceActivator(inputChannel = "loggingChannel")
	MessageHandler loggingHandler() {
		LoggingHandler loggingHandler = new LoggingHandler(LoggingHandler.Level.TRACE);
		loggingHandler.setLoggerName("tracing.data");
		return loggingHandler;
	}

	@Bean
	@IntegrationConverter
	Converter<Date, Integer> currentSeconds() {
		return new Converter<Date, Integer>() { // Not lambda for generic info presence

			@Override
			public Integer convert(Date date) {
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(date);
				return calendar.get(Calendar.SECOND);
			}

		};
	}

	@Bean
	public IntegrationFlow controlBus() {
		return IntegrationFlowDefinition::controlBus;
	}

	@Bean
	public IntegrationFlow controlBusControllerFlow(ControlBusGateway controlBusGateway) {
		return IntegrationFlows
				.from(WebFlux.inboundChannelAdapter("/control-bus/{endpointId}")
						.payloadExpression("#pathVariables.endpointId")
						.requestMapping(mapping -> mapping.methods(HttpMethod.GET)))
				.handle(controlBusGateway, "startEndpoint")
				.get();
	}

}
