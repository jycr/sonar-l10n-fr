#!/bin/bash

set -e
set -u

mvn clean package -DskipTests
docker build -f Dockerfile-test-deploy -t docker-l10n .
docker kill sonartest-deploy-l10n || echo "No running container"
docker rm sonartest-deploy-l10n

docker run --name sonartest-deploy-l10n -d -p 9000:9000 -p 3306:3306 docker-l10n

echo "Should be OK, open http://localhost:9000/"
