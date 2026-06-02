package com.poker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures Spring's STOMP-over-WebSocket message broker.
 *
 * <p>Connection endpoint: {@code ws://localhost:8080/ws}
 * (falls back to long-polling via SockJS at the same path).
 *
 * <p>Topics:
 * <ul>
 *   <li>{@code /topic/tables/{tableId}} — table-state broadcasts after every
 *       hand action (dealer seat, pot, street, per-seat stacks and folded flags)</li>
 * </ul>
 *
 * <p>The in-memory broker is sufficient for a single-node demo.  A production
 * deployment would replace it with a relay to an external STOMP broker
 * (RabbitMQ / ActiveMQ) for horizontal scalability.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Prefix for server-to-client topic destinations. */
    public static final String TOPIC_PREFIX = "/topic";

    /** Topic path template for table-state events. */
    public static final String TABLE_TOPIC = TOPIC_PREFIX + "/tables/";

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable the in-memory simple broker for /topic/** destinations.
        registry.enableSimpleBroker(TOPIC_PREFIX);
        // Inbound messages from clients must be prefixed with /app.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Primary WebSocket endpoint — native WebSocket.
        // SockJS fallback allows browsers that block WebSocket (corporate proxies)
        // to use HTTP long-polling transparently.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}
