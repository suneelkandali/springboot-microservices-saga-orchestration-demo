# Spring Boot Microservices Saga Orchestration Demo

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

```bash
git clone <your-repo-url>
cd springboot-microservices-saga-orchestration-demo
docker compose up --build -d
```

The first run may take a few minutes while Docker builds the images.

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
4. If a port is already in use, change the host port mapping in the Compose file.

## Notes

This demo is intended for local development and testing. It uses a simple in-memory-style saga flow with PostgreSQL-backed services.
