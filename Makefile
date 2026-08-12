.PHONY: install-deps-cached setup-hooks build generate lint lint-build integration test run dev docker-up docker-up-entra dep-insight ds-build ds-lint-build ds-integration ds-test

DATASTORE_LOCAL_INCLUDE=--include-build ../laa-info-and-advice-datastore

# One-off authenticated resolution of GitHub Packages deps, so ./gradlew works unauthenticated afterwards
install-deps-cached:
	GITHUB_ACTOR=default op run --env-file=.env -- ./gradlew clean compileTestJava

setup-hooks:
	./scripts/./setup-hooks.sh

build:
	./gradlew clean build

ds-build:
	./gradlew $(DATASTORE_LOCAL_INCLUDE) clean build

generate:
	./gradlew :record-controlled-work-api:openApiGenerate

lint:
	./gradlew spotlessApply

lint-build: lint build

ds-lint-build: lint ds-build

integration:
	./gradlew integrationTest

ds-integration:
	./gradlew $(DATASTORE_LOCAL_INCLUDE) integrationTest

test: lint-build integration

ds-test: ds-lint-build ds-integration

run:
	./gradlew bootRun

dev:
	./gradlew bootRun --args='--spring.profiles.active=local'


docker-build:
	op run --env-file=.env -- docker build \
		--secret id=git_token,env=GITHUB_TOKEN \
		-t laa-record-controlled-work-api .

docker-up:
	./docker/compose/up mock-issuer

# Sign in via real Entra ID instead of the default mock-oauth2-server - see .env.entra.
docker-up-entra:
	./docker/compose/up entra

dep-insight:
	./gradlew :record-controlled-work-api:dependencies --configuration runtimeClasspath 2>&1 | grep -B 5 -A 5 "$(dep)"