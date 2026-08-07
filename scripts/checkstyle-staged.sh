#!/usr/bin/env bash
set -euo pipefail

# Restrict Checkstyle to the staged Java files passed in by the pre-commit hook,
# instead of the whole codebase.
java_files=()
for f in "$@"; do
  if [[ "$f" == *.java ]]; then
    java_files+=("$f")
  fi
done

if [ ${#java_files[@]} -eq 0 ]; then
  exit 0
fi

joined_files=$(IFS=,; echo "${java_files[*]}")
./gradlew checkstyleAll --quiet "-PcheckstyleFiles=${joined_files}"
