# Spring Boot Microservices Saga Orchestration Demo

![Quick Start](https://img.shields.io/badge/Quick%20Start-Docker-blue)

This project demonstrates a simple saga-based workflow using Spring Boot microservices, PostgreSQL, and Docker Compose.

It includes:
- an order service
- an inventory service
- an orchestrator service
- a PostgreSQL database

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

## Notes

This demo is intended for local development and testing. It uses a simple in-memory-style saga flow with PostgreSQL-backed services.
