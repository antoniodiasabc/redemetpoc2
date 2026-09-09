#!/bin/bash

echo "🐳 Subindo ambiente POC SIGMET..."

docker compose down

docker compose build --no-cache

docker compose up -d

echo "📊 Status:"
docker compose ps
