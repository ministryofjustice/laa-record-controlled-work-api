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


GITHUB_ACTOR ?= $(shell grep 'project.ext.gitPackageUser' ~/.gradle/gradle.properties 2>/dev/null | cut -d= -f2 | tr -d ' ')
GITHUB_TOKEN ?= $(shell grep 'project.ext.gitPackageKey' ~/.gradle/gradle.properties 2>/dev/null | cut -d= -f2 | tr -d ' ')

export GITHUB_ACTOR
export GITHUB_TOKEN

docker-build:
	docker build \
		--secret id=github_actor,env=GITHUB_ACTOR \
		--secret id=github_token,env=GITHUB_TOKEN \
		-t laa-record-controlled-work-api .

docker-up:
	docker compose up --build