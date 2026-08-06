.PHONY: install-deps-cached setup-hooks build generate lint lint-build integration test run dev docker-up docker-up-entra dep-insight

# One-off authenticated resolution of GitHub Packages deps, so ./gradlew works unauthenticated afterwards
install-deps-cached:
	GITHUB_ACTOR=default op run --env-file=.env -- ./gradlew clean compileTestJava

setup-hooks:
	./scripts/./setup-hooks.sh

build:
	./gradlew clean build

generate:
	./gradlew :record-controlled-work-api:openApiGenerate

lint:
	./gradlew spotlessApply

lint-build: lint build

integration:
	./gradlew integrationTest

test: lint-build integration

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