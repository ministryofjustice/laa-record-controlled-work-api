.PHONY: setup-hooks build lint integration run dev docker-up

setup-hooks:
	./scripts/./setup-hooks.sh

build:
	./gradlew clean build

lint:
	./gradlew spotlessApply

integration:
	./gradlew integrationTest

run:
	./gradlew bootRun

dev:
	./gradlew bootRun --args='--spring.profiles.active=local'


docker-up:
	docker compose up