.PHONY: build lint

setup-hooks:
	./scripts/./setup-hooks.sh

build:
	./gradlew clean build

lint:
	./gradlew spotlessApply