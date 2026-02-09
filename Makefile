# Global Makefile for Hybrid Retrieval and Ranking Engine

.PHONY: build up down clean logs benchmark

COMPOSE_PROJECT_NAME ?= hybrid-retrieval-and-ranking-engine

build:
	@echo "Building all services..."
	mvn clean package -DskipTests

up:
	@echo "Starting all containers..."
	docker-compose -p $(COMPOSE_PROJECT_NAME) up --build -d

down:
	@echo "Stopping all containers..."
	docker-compose -p $(COMPOSE_PROJECT_NAME) down

clean:
	@echo "Cleaning build artifacts..."
	mvn clean
	docker system prune -f

logs:
	@echo "Viewing logs..."
	docker-compose -p $(COMPOSE_PROJECT_NAME) logs -f

benchmark:
	@echo "Running hybrid benchmark..."
	./scripting/benchmark-hybrid.sh
