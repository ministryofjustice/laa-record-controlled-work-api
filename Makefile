.PHONY: setup-hooks build generate lint lint-build gitleaks integration run dev docker-up

setup-hooks:
	./scripts/./setup-hooks.sh

build:
	./gradlew clean build

generate:
	./gradlew :record-controlled-work-api:openApiGenerate

lint:
	./gradlew spotlessApply

lint-build: lint build

gitleaks:
	prek run baseline --all-files

integration:
	./gradlew integrationTest

run:
	./gradlew bootRun

dev:
	./gradlew bootRun --args='--spring.profiles.active=local'


docker-build:
	op run --env-file=.env -- docker build \
		--secret id=github_actor,env=GITHUB_ACTOR \
		--secret id=github_token,env=GITHUB_TOKEN \
		-t laa-record-controlled-work-api .

docker-up:
	op run --env-file=.env -- docker compose up --build