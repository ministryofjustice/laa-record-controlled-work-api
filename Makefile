.PHONY: build lint

build:
	./gradlew clean build

lint:
	./gradlew spotlessApply