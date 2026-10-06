#!/usr/bin/env bash
set -euo pipefail

# Scope a Gradle task to the staged Java files passed in by the pre-commit hook,
# instead of the whole codebase.
#
# Usage: run-staged-gradle-task.sh <gradle-task> <gradle-property> <file>...
task="$1"
property="$2"
shift 2

java_files=()
for f in "$@"; do
  if [[ "$f" == *.java ]]; then
    java_files+=("$f")
  fi
done

if [ ${#java_files[@]} -eq 0 ]; then
  exit 0
fi

if [[ "$task" == "spotlessApply" ]]; then
  service_module="record-controlled-work-service"
  service_files=()
  service_file_count=0
  for file in "${java_files[@]}"; do
    if [[ "$file" == "$service_module/"* ]]; then
      service_files+=("$file")
      service_file_count=$((service_file_count + 1))
    fi
  done
  if [ "$service_file_count" -gt 0 ]; then
    java_files=("${service_files[@]}")
  else
    java_files=()
  fi
  task=":${service_module}:${task}"
fi

if [ ${#java_files[@]} -eq 0 ]; then
  exit 0
fi

joined_files=$(IFS=,; echo "${java_files[*]}")
./gradlew "$task" --quiet "-P${property}=${joined_files}"
