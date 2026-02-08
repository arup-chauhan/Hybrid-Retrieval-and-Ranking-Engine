# Global Makefile for Hybrid Retrieval and Ranking Engine

.PHONY: build up down clean logs benchmark

build:
	@echo "🔧 Building all services..."
	mvn clean package -DskipTests

up:
	@echo "🚀 Starting all containers..."
	docker-compose up --build -d

down:
	@echo "🛑 Stopping all containers..."
	docker-compose down

clean:
	@echo "🧹 Cleaning build artifacts..."
	mvn clean
	docker system prune -f

logs:
	@echo "📜 Viewing logs..."
	docker-compose logs -f

benchmark:
	@echo "📈 Running hybrid benchmark..."
	./scripting/benchmark-hybrid.sh
