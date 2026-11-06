package com.example.engine.config;

import com.example.engine.messaging.retry.BackoffPolicy;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(EngineProperties.class)
public class EngineConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    BackoffPolicy backoffPolicy(EngineProperties props) {
        return new BackoffPolicy(props.retryTiers());
    }

    /**
     * Named differently from Boot's default "rabbitListenerContainerFactory": Boot forbids silent
     * bean overriding, so a clash would fail startup. Rejected messages are not requeued.
     */
    @Bean
    SimpleRabbitListenerContainerFactory taskListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                      Jackson2JsonMessageConverter converter,
                                                                      EngineProperties props) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setConcurrentConsumers(props.concurrency());
        factory.setMaxConcurrentConsumers(props.maxConcurrency());
        factory.setPrefetchCount(props.prefetch());
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
