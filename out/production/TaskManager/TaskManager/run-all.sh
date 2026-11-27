#!/bin/bash
# Compile once; isolate each entry point's singleton state in its own JVM.
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
javac -d "$classes" TaskManager/*/*.java
unset LLM_API_KEY  # The live-provider smoke test will report a skip.
for app in Main TestRunner TextCreationTest SubtaskTest IdempotencyTest QueryTest EventTest PromptAndEnvWalkthrough LlmClientSelfCheck LlmSmokeTest; do
    printf '\n=== %s ===\n' "$app"
    java -cp "$classes" "TaskManager.app.$app"
done
printf '\nAll app entry points finished. No live provider was called.\n'
