package com.poker.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the gRPC {@link ManagedChannel} that connects to the Go odds service.
 *
 * <p>Only created when {@code go-odds.enabled=true} is set (e.g. via the
 * {@code GO_ODDS_ENABLED} environment variable in Docker Compose).  When the
 * property is absent or false the {@link com.poker.service.StubEquityProvider}
 * is used instead.
 */
@Configuration
@ConditionalOnProperty(name = "go-odds.enabled", havingValue = "true")
public class GrpcConfig {

    /**
     * Managed channel to the go-odds gRPC service.
     *
     * <p>The {@code destroyMethod = "shutdown"} instructs Spring to call
     * {@link ManagedChannel#shutdown()} during context close so in-flight RPCs
     * are given a chance to complete before the JVM exits.
     */
    @Bean(destroyMethod = "shutdown")
    public ManagedChannel oddsServiceChannel(
            @Value("${go-odds.host:localhost}") String host,
            @Value("${go-odds.port:50051}") int     port) {

        return ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
    }
}
