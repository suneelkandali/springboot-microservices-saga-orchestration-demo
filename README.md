# Spring Boot Microservices Saga and Circuit Breaker Orchestration Demo

![Quick Start](https://img.shields.io/badge/Quick%20Start-Docker-blue)

This project demonstrates a simple saga-based workflow and circuit breaker pattern using Spring Boot microservices, PostgreSQL, and Docker Compose.

It includes:
- an order service
- an inventory service
- an orchestrator service
- a PostgreSQL database

## Saga workflow 

- Orchestrator receives an order request.
- Orchestrator calls Order Service to create an order in a PENDING state.
- Orchestrator calls Inventory Service to reserve stock.
- Success: Orchestrator calls Order Service to update the status to COMPLETED.
- Failure (Compensating Transaction): If stock is insufficient, the Inventory Service rejects the request. The Orchestrator catches this and calls the Order Service to CANCEL the order.

## Circuit Breaker pattern

The orchestrator now uses a circuit breaker around the calls to the order and inventory services.

Why this helps:
- If the downstream service is unavailable or keeps failing, the orchestrator stops repeatedly trying the same failing call.
- After a configured number of failures, the circuit opens and the orchestrator immediately uses a fallback instead of hammering the dependency.
- After a cool-down period, the circuit transitions to half-open state and allows a small number of test calls before fully closing again.

In this demo the breaker is configured with:
- sliding window of recent calls
- minimum number of calls before evaluating failures
- failure-rate threshold
- open-state wait duration
- half-open recovery probes

## Prerequisites on a Mac

Make sure the following are installed and running:
- Docker Desktop for Mac
- Docker Compose plugin
- Git
- curl (usually already available on macOS)

Verify Docker is available:

```bash
docker --version
docker compose version
```

## Clone and run locally

Clone the repository and start the full stack (Mac):

```bash
git clone https://github.com/suneelkandali/springboot-microservices-saga-orchestration-demo
cd springboot-microservices-saga-orchestration-demo
docker compose up --build -d
```

The first run may take a few minutes while Docker builds the images and downloads base images. Wait until `postgres-db` reports healthy in `docker compose ps` or check logs.

## Services and ports

Once the containers are running:
- Order service: http://localhost:8083
- Inventory service: http://localhost:8084
- Orchestrator service: http://localhost:8085
- PostgreSQL: localhost:5433

## Check container status

```bash
docker compose ps
```

You should see the PostgreSQL, order, inventory, and orchestrator containers in the `Up` state.

## Verify the database seed data

The PostgreSQL container initializes a sample inventory row:
- product ID: 101
- stock: 5

You can verify it with:

```bash
docker exec -it saga-postgres psql -U postgres -d saga_inventory_db -c "SELECT * FROM inventory;"
```

## Test the application

Use the orchestrator endpoint to trigger a checkout flow:

```bash
curl -X POST http://localhost:8085/api/saga/checkout \
  -H "Content-Type: application/json" \
   -d '{"productId":101,"quantity":2,"price":99.99}'
```

Expected response:

```text
Saga Complete: Order Processed and Finalized successfully.
```

## Demo: Compensation (insufficient inventory)

The seed inventory contains product `101` with stock `5`. The following sequence demonstrates depletion and a subsequent checkout that triggers the saga compensation (rollback) path.

1. Check inventory (before):

```bash
docker exec -it saga-postgres psql -U postgres -d saga_inventory_db -c "SELECT * FROM inventory;"
```

2. Successful checkout (reduce stock by 2):

```bash
curl -X POST http://localhost:8085/api/saga/checkout \
   -H "Content-Type: application/json" \
   -d '{"productId":101,"quantity":2,"price":99.99}'
```

3. Successful checkout (reduce stock by 2 again):

```bash
curl -X POST http://localhost:8085/api/saga/checkout \
   -H "Content-Type: application/json" \
   -d '{"productId":101,"quantity":2,"price":99.99}'
```

4. Check inventory (should show 1 left):

```bash
docker exec -it saga-postgres psql -U postgres -d saga_inventory_db -c "SELECT * FROM inventory;"
```

5. Checkout that exceeds remaining stock (triggers compensation):

```bash
curl -X POST http://localhost:8085/api/saga/checkout \
   -H "Content-Type: application/json" \
   -d '{"productId":101,"quantity":2,"price":99.99}'
```

Expected result: the orchestrator will follow the failure path and perform compensation (order cancellation). The response or logs will indicate a rollback/compensation action.

You can inspect service logs to see the compensation flow in detail:

```bash
docker compose logs -f orchestrator-service
docker compose logs -f inventory-service
```



## How to test the circuit breaker locally

1. Start the stack:

```bash
docker compose up --build -d
```

2. Confirm the orchestrator is healthy:

```bash
curl -sS http://localhost:8085/actuator/health
curl -sS http://localhost:8085/actuator/health/readiness
curl -sS http://localhost:8085/actuator/health/liveness
curl -sS http://localhost:8085/actuator/metrics
curl -sS http://localhost:8085/actuator/metrics/resilience4j.circuitbreaker.calls
```

3. Simulate repeated downstream failures by stopping the target dependency temporarily (for example, stop the inventory service container):

```bash
docker compose stop inventory-service
```

4. Trigger several requests to the orchestrator in a row:

```bash
for i in 1 2 3 4 5 6 7 8 9 10; do
   echo "Request $i"
   curl -sS -X POST http://localhost:8085/api/saga/checkout \
      -H "Content-Type: application/json" \
      -d '{"productId":101,"quantity":2,"price":99.99}'
   echo
done
```

5. Watch the orchestrator logs as the breaker opens and falls back:

```bash
docker compose logs -f orchestrator-service
```

6. Bring the dependency back up:

```bash
docker compose start inventory-service
```

7. After the wait duration has passed, send a few more requests to observe the breaker transition from open to half-open and then close again.

Expected behavior:
- The first few requests may fail or trigger rollback behavior.
- Once the failure threshold is reached, the breaker opens.
- Further requests should short-circuit quickly through the fallback path instead of continuing to hit the unavailable service.

## Useful commands

Stop everything:

```bash
docker compose down
```

Stop everything and remove volumes:

```bash
docker compose down -v
```

View logs for a service:

```bash
docker compose logs -f orchestrator-service
```

## Health check

If the Spring Boot Actuator is enabled you can check service health:

```bash
curl -sS http://localhost:8085/actuator/health
```

Fallback quick HTTP check (returns HTTP status code):

```bash
curl -sS -o /dev/null -w "%{http_code}\n" http://localhost:8085/ || echo "no response"
```

## Postman collection

You can import a ready Postman collection to exercise the checkout flow: [postman/collection.json](postman/collection.json)


## Troubleshooting

If the app does not start correctly:

1. Make sure Docker Desktop is running.
2. Check container logs:
   ```bash
   docker compose logs
   ```
3. Rebuild and restart:
   ```bash
   docker compose down
   docker compose up --build -d
   ```
5. If Docker Desktop prompts for more resources (memory/CPUs), increase them and retry.
6. If containers start but services are unreachable from the host, check the mapped host ports in `docker compose ps`.
4. If a port is already in use, change the host port mapping in the Compose file.

## Troubleshooting the circuit breaker

If the circuit breaker does not appear to trigger (e.g. the fallback methods are never called and the metrics always show zero calls), check the following:

### 1. Method visibility

The `@CircuitBreaker` annotation relies on Spring AOP proxies. **Methods annotated with `@CircuitBreaker` must be `public`**, not `private`. Spring AOP cannot intercept `private` method calls, so the circuit breaker aspect will never be invoked.

```java
// ❌ Will NOT trigger the circuit breaker
@CircuitBreaker(name = "inventoryServiceCircuitBreaker", fallbackMethod = "fallbackReserveInventory")
private InventoryResponse reserveInventory(InventoryRequest invRequest) { ... }

// ✅ Will trigger the circuit breaker
@CircuitBreaker(name = "inventoryServiceCircuitBreaker", fallbackMethod = "fallbackReserveInventory")
public InventoryResponse reserveInventory(InventoryRequest invRequest) { ... }
```

### 2. Self-invocation

When a method inside the same class calls another `@CircuitBreaker`-annotated method, the call bypasses the proxy. Use a self-reference with `@Lazy` to ensure the proxy is used:

```java
@Service
public class SagaOrchestratorService {
    private final SagaOrchestratorService self;

    @Autowired
    public SagaOrchestratorService(@Lazy SagaOrchestratorService self) {
        this.self = self;
    }

    public String executeSaga(OrderRequest orderRequest) {
        // Use self. instead of this. to go through the proxy
        OrderResponse orderResponse = self.createOrder(orderRequest);
        InventoryResponse invResponse = self.reserveInventory(invRequest);
        // ...
    }
}
```

### 3. Missing AOP dependency

The `resilience4j-spring-boot3` starter requires `spring-boot-starter-aop` on the classpath for the `@CircuitBreaker` aspect to work. Without it, the logs will show:

```
Aspects are not activated because AspectJ is not on the classpath.
```

Add the dependency to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### 4. Verify circuit breaker is working

After applying the fixes above, you can confirm the circuit breaker is active by checking:

- **Actuator metrics** show non-zero call counts:
  ```bash
  curl -sS http://localhost:8085/actuator/metrics/resilience4j.circuitbreaker.calls?tag=name:inventoryServiceCircuitBreaker
  ```

- **Logs** show the `CircuitBreakerAspect` intercepting calls:
  ```
  Created or retrieved circuit breaker 'inventoryServiceCircuitBreaker' with failure rate '50.0'
  ```

- **State transitions** are logged by the `CircuitBreakerEventLogger`:
  ```
  Circuit breaker [inventoryServiceCircuitBreaker] state transition: CLOSED -> OPEN
  Circuit breaker [inventoryServiceCircuitBreaker] rejected a call because it is open or half-open
  ```

- **Fallback methods** are invoked instead of throwing exceptions:
  ```
  Circuit breaker fallback triggered for inventory service: CircuitBreaker 'inventoryServiceCircuitBreaker' is OPEN and does not permit further calls
  ```

## Notes

This demo is intended for local development and testing. It uses a simple in-memory-style saga flow with PostgreSQL-backed services.
