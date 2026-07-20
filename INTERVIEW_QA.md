# Interview Q&A: Spring Boot Microservices Saga & Circuit Breaker Orchestration Demo

## Saga Pattern & Distributed Transactions

### 1. What is the Saga pattern and how does it solve distributed transaction problems in microservices?

**Answer:** The Saga pattern is a design pattern that manages distributed transactions across multiple microservices by breaking them into a sequence of local transactions, each with a compensating action for rollback. In this demo, the saga involves three steps: create an order (PENDING), reserve inventory, and confirm the order (COMPLETED). If inventory reservation fails, the saga triggers a compensating transaction to cancel the order. This avoids the need for distributed ACID transactions (like two-phase commit) which don't scale well in microservice architectures.

### 2. Explain the difference between choreography-based and orchestration-based saga patterns.

**Answer:** In choreography-based sagas, each service publishes events and other services react independently — there is no central coordinator. In orchestration-based sagas (used in this demo), a central orchestrator service (`SagaOrchestratorService`) explicitly tells each service what to do and handles failure/compensation logic. The orchestrator approach provides better visibility, centralized error handling, and is easier to debug, but introduces a single point of coordination.

### 3. What is a compensating transaction and when is it triggered in this application?

**Answer:** A compensating transaction is an action that undoes the effects of a previous transaction when the saga fails. In this demo, when inventory reservation fails (either due to insufficient stock or service unavailability), the orchestrator calls `rollbackOrder(orderId)` which sends a PUT request to the order service's `/cancel` endpoint to change the order status from PENDING to CANCELED.

### 4. How does the orchestrator service coordinate the saga workflow between order and inventory services?

**Answer:** The `SagaOrchestratorService.executeSaga()` method follows a sequential workflow: (1) calls `self.createOrder()` to create an order in PENDING state, (2) calls `self.reserveInventory()` to reserve stock, (3) if successful, calls `self.confirmOrder()` to mark the order COMPLETED, (4) if any step fails, calls `self.rollbackOrder()` to cancel the order. Each of these methods uses `RestClient` to make HTTP calls to the respective downstream services.

---

## Circuit Breaker Pattern (Resilience4j)

### 5. What problem does the circuit breaker pattern solve in microservice architectures?

**Answer:** The circuit breaker prevents cascading failures by stopping repeated calls to a failing downstream service. Without it, the orchestrator would keep making HTTP requests to an unavailable service, wasting resources, increasing latency, and potentially exhausting thread pools. When the circuit is OPEN, calls are immediately rejected and the fallback is invoked, allowing the system to fail fast and recover gracefully.

### 6. Explain the three states of a circuit breaker: CLOSED, OPEN, and HALF_OPEN.

**Answer:** 
- **CLOSED**: Normal operation — requests pass through to the downstream service. Failures are tracked against a threshold.
- **OPEN**: The failure threshold has been exceeded — requests are immediately rejected without calling the downstream service. After a configured `waitDurationInOpenState`, it transitions to HALF_OPEN.
- **HALF_OPEN**: A limited number of test requests are allowed through. If they succeed, the circuit closes. If they fail, it goes back to OPEN.

### 7. What is the `failureRateThreshold` and how does it determine when a circuit breaker opens?

**Answer:** `failureRateThreshold` (set to 50% in this demo) defines the percentage of failed calls required to open the circuit. For example, with `minimumNumberOfCalls=3` and `slidingWindowSize=5`, if 3 out of 3 calls fail (100% failure rate), the threshold is exceeded and the circuit opens. If only 1 out of 3 calls failed (33%), the circuit would remain closed.

### 8. How does `slidingWindowSize` affect the circuit breaker's failure evaluation?

**Answer:** `slidingWindowSize` (set to 5 in this demo) defines how many recent calls are considered when calculating the failure rate. With `COUNT_BASED` type, the circuit breaker tracks the last N calls. Once the window has at least `minimumNumberOfCalls` (3) recorded calls, it evaluates whether the failure rate exceeds the threshold.

### 9. What is the purpose of `permittedNumberOfCallsInHalfOpenState`?

**Answer:** `permittedNumberOfCallsInHalfOpenState` (set to 2 in this demo) controls how many test requests are allowed through when the circuit is HALF_OPEN. If all permitted calls succeed, the circuit transitions back to CLOSED. If any fail, it goes back to OPEN. This prevents overwhelming a recovering service with traffic.

### 10. What does `waitDurationInOpenState` control and why is it important?

**Answer:** `waitDurationInOpenState` (set to 600 seconds in this demo) defines how long the circuit stays OPEN before transitioning to HALF_OPEN. This gives the downstream service time to recover. A value that's too short may cause rapid toggling, while too long delays recovery unnecessarily.

---

## Spring AOP & Annotation-Based Configuration

### 11. Why must methods annotated with `@CircuitBreaker` be `public` rather than `private`?

**Answer:** Spring AOP uses either JDK dynamic proxies or CGLIB proxies to intercept method calls. Proxies can only intercept `public` method calls — `private` methods are not visible to the proxy. When a `@CircuitBreaker` annotation is on a `private` method, the annotation is silently ignored and the circuit breaker never triggers. This was a bug found in the original code of this demo.

### 12. What is the self-invocation problem in Spring AOP and how does the `@Lazy` self-reference pattern solve it?

**Answer:** When a method in the same class calls another method directly using `this.method()`, the call bypasses the proxy, so any `@CircuitBreaker` annotation on the called method is ignored. The solution is to inject a self-reference using `@Autowired` with `@Lazy` and call methods via `self.method()` instead of `this.method()`. This ensures calls go through the proxy where the aspect can intercept them.

### 13. Why does the `spring-boot-starter-aop` dependency need to be on the classpath for Resilience4j annotations to work?

**Answer:** Resilience4j's `@CircuitBreaker` annotation is implemented as an AspectJ aspect. The `spring-boot-starter-aop` dependency brings in Spring AOP with AspectJ support, which enables the annotation processing. Without it, the `CircuitBreakerAspect` is not activated and the annotations are never processed, even though the `resilience4j-spring-boot3` library is present.

### 14. What does the "Aspects are not activated because AspectJ is not on the classpath" log message indicate?

**Answer:** This DEBUG-level log from Resilience4j indicates that the `CircuitBreakerAspect` cannot be registered because Spring AOP/AspectJ support is not available. The `@CircuitBreaker` annotations will be completely ignored. The fix is to add `spring-boot-starter-aop` as a dependency.

### 15. How does the `@CircuitBreaker` annotation with a `fallbackMethod` parameter work?

**Answer:** The `@CircuitBreaker(name = "inventoryServiceCircuitBreaker", fallbackMethod = "fallbackReserveInventory")` annotation tells Resilience4j to wrap the method with circuit breaker logic. When the circuit is CLOSED and the method throws an exception, the failure is recorded. If the circuit is OPEN, the annotated method is not called — instead, the `fallbackMethod` is invoked with the same parameters plus a `Throwable` parameter. The fallback method must be in the same class and have a compatible signature.

---

## Resilience4j Configuration

### 16. What is the difference between `COUNT_BASED` and `TIME_BASED` sliding window types?

**Answer:** `COUNT_BASED` (used in this demo) tracks the last N calls regardless of time. `TIME_BASED` tracks calls within a sliding time window (e.g., last 60 seconds). COUNT_BASED is simpler and more predictable for testing, while TIME_BASED is better for production where traffic patterns vary.

### 17. What does `minimumNumberOfCalls` represent and why is it important?

**Answer:** `minimumNumberOfCalls` (set to 3 in this demo) is the minimum number of calls that must be recorded in the sliding window before the circuit breaker can calculate the failure rate. This prevents the circuit from opening prematurely due to a small sample size. For example, if the first call fails, the circuit won't open until at least 3 calls are recorded.

### 18. How does `registerHealthIndicator: true` in the circuit breaker configuration integrate with Spring Boot Actuator?

**Answer:** When `registerHealthIndicator: true` is set, the circuit breaker state is exposed through the `/actuator/health` endpoint. This allows monitoring systems to detect when circuit breakers are OPEN and potentially trigger alerts. The health endpoint will show the state of each circuit breaker as part of the overall application health.

### 19. What is `automaticTransitionFromOpenToHalfOpenEnabled` and when would you enable it?

**Answer:** This setting (enabled in this demo) allows the circuit breaker to automatically transition from OPEN to HALF_OPEN after the `waitDurationInOpenState` expires, without requiring an external call. When disabled, the transition only happens when a call is made while the circuit is OPEN. Enabling it ensures the circuit can recover even during low-traffic periods.

---

## Spring Boot & Actuator

### 20. What endpoints does Spring Boot Actuator expose for monitoring the circuit breaker?

**Answer:** The actuator exposes: `/actuator/health` (circuit breaker state when `registerHealthIndicator=true`), `/actuator/metrics/resilience4j.circuitbreaker.calls` (call counts by result type), `/actuator/metrics/resilience4j.circuitbreaker.state` (current state), and `/actuator/metrics` (all available metrics).

### 21. How would you check the current state of a specific circuit breaker using actuator metrics?

**Answer:** Use the metrics endpoint with a tag filter: `curl -sS "http://localhost:8085/actuator/metrics/resilience4j.circuitbreaker.state?tag=name:inventoryServiceCircuitBreaker"`. The response includes a measurement with `VALUE` where 1.0 = CLOSED, 2.0 = OPEN, 3.0 = HALF_OPEN, etc.

### 22. How do liveness and readiness probes work with Spring Boot Actuator in this application?

**Answer:** The application has liveness and readiness probe endpoints enabled (`/actuator/health/liveness` and `/actuator/health/readiness`). Liveness indicates the application is running, while readiness indicates it's ready to handle traffic. These are used by container orchestration platforms (like Kubernetes) to manage the application lifecycle.

---

## Docker & Containerization

### 23. Explain the Docker multi-stage build used in the orchestrator-service Dockerfile.

**Answer:** The Dockerfile uses two stages: (1) a `build` stage using `maven:3.9.9-eclipse-temurin-17` that compiles the Java code with `mvn -B -DskipTests package`, and (2) a `runtime` stage using `eclipse-temurin:17-jre-jammy` that only copies the built JAR from the build stage. This keeps the final image small by excluding Maven and build dependencies.

### 24. How does Docker Compose handle service dependencies and startup ordering in this project?

**Answer:** The `depends_on` configuration specifies startup order. The `postgres-db` service uses a `healthcheck` that runs `pg_isready`, and the order-service and inventory-service use `condition: service_healthy` to wait for PostgreSQL to be ready before starting. The orchestrator-service depends on order-service and inventory-service but without a health condition, so it may start before they're ready.

### 25. What is the purpose of the `networks` configuration in docker-compose.yml and why are all services on the same network?

**Answer:** All services are on the `saga-network` bridge network, which allows them to communicate using service names (e.g., `http://inventory-service:8082`) instead of IP addresses. Docker's internal DNS resolves service names to container IPs within the same network. This is essential for the orchestrator to call order and inventory services by their logical names.

---

## Exception Handling & Fallback

### 26. What happens when a fallback method returns `null` in a saga workflow?

**Answer:** When `fallbackReserveInventory` returns `null`, the `executeSaga` method checks `invResponse != null && invResponse.success()`. Since `invResponse` is `null`, this condition is false, and the orchestrator proceeds to call `self.rollbackOrder(orderId)` to cancel the order. The saga then returns "Saga Rolled Back: Inventory allocation failed -> Unknown execution error".

### 27. How does the orchestrator handle the case when the inventory service is completely unavailable vs when it returns an insufficient stock response?

**Answer:** When the inventory service is unavailable (stopped container), the `RestClient` throws a `ResourceAccessException`, which triggers the circuit breaker fallback returning `null`. When the service is available but stock is insufficient, the inventory controller returns an `InventoryResponse` with `success=false` and a message like "Insufficient stock". Both cases result in a rollback, but the error message differs: "Unknown execution error" for unavailability vs the actual message from the service.

---

## Spring RestClient

### 28. How does `RestClient` simplify HTTP calls compared to `RestTemplate` in Spring Boot 3.x?

**Answer:** `RestClient` (introduced in Spring Boot 3.2) provides a fluent, reactive-style API similar to `WebClient` but with synchronous behavior. It uses method chaining: `restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(ResponseClass.class)`. It's more intuitive and type-safe than the older `RestTemplate`.

### 29. What exception does `RestClient` throw when a connection fails to a downstream service?

**Answer:** `RestClient` throws `org.springframework.web.client.ResourceAccessException` when it cannot connect to the target service (e.g., connection refused, DNS resolution failure). This exception wraps the underlying `java.nio.channels.UnresolvedAddressException` or `java.net.ConnectException`.

---

## Microservices Communication

### 30. How do the services discover each other in this Docker Compose setup?

**Answer:** Docker Compose provides built-in DNS resolution within the custom `saga-network` bridge network. Services communicate using container names as hostnames: the orchestrator uses `http://order-service:8081` and `http://inventory-service:8082` (configured via `SERVICE_ORDER` and `SERVICE_INVENTORY` environment variables). This is a simple alternative to a full service discovery solution like Eureka or Consul.

### 31. What would need to change if you wanted to deploy these services to Kubernetes with service discovery?

**Answer:** In Kubernetes, you would create Service resources for each microservice, and Kubernetes DNS would resolve service names automatically. The environment variables would use Kubernetes service names (e.g., `http://order-service:8081`). You'd also need to replace Docker Compose healthchecks with Kubernetes liveness/readiness probes, and use ConfigMaps/Secrets for configuration.

---

## Practical Troubleshooting

### 32. You deploy the circuit breaker but the fallback method is never called and metrics show zero calls. What are the possible causes?

**Answer:** Three common causes: (1) The `@CircuitBreaker`-annotated methods are `private` — Spring AOP cannot intercept private methods. (2) The `spring-boot-starter-aop` dependency is missing — the `CircuitBreakerAspect` is not activated. (3) Self-invocation — the annotated method is called via `this.method()` instead of `self.method()`, bypassing the proxy.

### 33. A method with `@CircuitBreaker` works correctly when called from another class but not when called from within the same class. Why?

**Answer:** This is the self-invocation problem. When called from another class, the call goes through the Spring proxy which applies the circuit breaker aspect. When called from within the same class using `this.method()`, the call bypasses the proxy entirely. The fix is to inject a self-reference with `@Lazy` and call `self.method()` instead.

### 34. The orchestrator logs show "Aspects are not activated because AspectJ is not on the classpath". What dependency is missing?

**Answer:** The `spring-boot-starter-aop` dependency is missing from `pom.xml`. This dependency brings in Spring AOP with AspectJ support, which is required for the `@CircuitBreaker` annotation processing. Without it, the `CircuitBreakerAspect` cannot be registered and all circuit breaker annotations are silently ignored.

---

## General Architecture

### 35. Why are DTOs like `OrderRequest`, `OrderResponse`, `InventoryRequest`, and `InventoryResponse` kept in the orchestrator service rather than in a shared library?

**Answer:** In this demo, the DTOs are duplicated across services (the orchestrator's DTOs are copied to order-service and inventory-service during Docker build). In a production system, these would typically be in a shared library (JAR) published to a private Maven repository. The current approach keeps the demo self-contained without requiring an external artifact repository.

### 36. How would you extend this application to add a payment service to the saga workflow?

**Answer:** You would: (1) Create a new payment-service with its own database and controller, (2) Add a `PaymentRequest`/`PaymentResponse` DTO, (3) Add a new `@CircuitBreaker`-annotated method in `SagaOrchestratorService` for processing payment, (4) Insert the payment step between inventory reservation and order confirmation, (5) Add a compensating transaction for payment failure (refund), (6) Add the service to docker-compose.yml with appropriate environment variables.

### 37. What are the trade-offs of using an orchestrator-based saga vs a choreography-based approach?

**Answer:** Orchestrator-based (used here): Pro — centralized control, easier to understand and debug, explicit workflow definition. Con — single point of failure, orchestrator can become a bottleneck, tighter coupling. Choreography-based: Pro — decentralized, services are loosely coupled, better scalability. Con — harder to trace the overall workflow, implicit coordination via events, risk of circular dependencies.

### 38. How does the CircuitBreakerEventLogger component help with debugging circuit breaker behavior?

**Answer:** The `CircuitBreakerEventLogger` attaches listeners to all circuit breakers in the registry and logs state transitions (CLOSED -> OPEN, OPEN -> HALF_OPEN, etc.), rejected calls (when circuit is OPEN), and recorded errors. This provides real-time visibility into circuit breaker behavior without needing to query actuator endpoints, making it easier to debug during development and testing.