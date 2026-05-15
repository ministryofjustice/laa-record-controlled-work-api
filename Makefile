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


docker-build:
	docker build \
		--secret id=gradle_props,src=$(HOME)/.gradle/gradle.properties \
		-t laa-record-controlled-work-api .

docker-up:
	docker compose up --build