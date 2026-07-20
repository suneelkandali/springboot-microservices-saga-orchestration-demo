package com.example.saga.orchestrator.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class CircuitBreakerEventLogger {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerEventLogger.class);

    private final CircuitBreakerRegistry registry;

    public CircuitBreakerEventLogger(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void attachListeners() {
        registry.getAllCircuitBreakers().forEach(this::attachToBreaker);
    }

    private void attachToBreaker(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("Circuit breaker [{}] state transition: {} -> {}",
                        event.getCircuitBreakerName(),
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState())
        );

        circuitBreaker.getEventPublisher().onCallNotPermitted(event ->
                log.warn("Circuit breaker [{}] rejected a call because it is open or half-open",
                        event.getCircuitBreakerName())
        );

        circuitBreaker.getEventPublisher().onError(event ->
                log.warn("Circuit breaker [{}] recorded a failure: {}",
                        event.getCircuitBreakerName(),
                        event.getThrowable().getMessage())
        );
    }
}
