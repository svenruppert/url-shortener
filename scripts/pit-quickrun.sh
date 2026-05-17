#!/usr/bin/env bash
#
# pit-quickrun.sh — scoped PIT mutation run for the inner dev loop.
#
# Usage:
#   scripts/pit-quickrun.sh                      # default: ui views + browserless tests
#   scripts/pit-quickrun.sh <module>             # e.g. urlshortener-core
#   scripts/pit-quickrun.sh <module> <prod-pat> <test-pat>
#
# Examples:
#   scripts/pit-quickrun.sh
#   scripts/pit-quickrun.sh urlshortener-core
#   scripts/pit-quickrun.sh urlshortener-server \
#       'com.svenruppert.urlshortener.api.security.*' \
#       'junit.com.svenruppert.urlshortener.api.security.*'
#
# Reactor-wide runs belong in CI — keep this loop tight (single module,
# scoped patterns) so a typical run finishes in 1–3 min.

set -euo pipefail

cd "$(dirname "$0")/.."

MODULE="${1:-urlshortener-ui}"
PROD_DEFAULT='com.svenruppert.urlshortener.ui.vaadin.views.*'
TEST_DEFAULT='junit.com.svenruppert.urlshortener.ui.browserless.*'

PROD_PATTERN="${2:-$PROD_DEFAULT}"
TEST_PATTERN="${3:-$TEST_DEFAULT}"

if [[ "$MODULE" == "urlshortener-core" && $# -lt 2 ]]; then
  PROD_PATTERN='com.svenruppert.urlshortener.core.*'
  TEST_PATTERN='junit.com.svenruppert.urlshortener.core.*'
elif [[ "$MODULE" == "urlshortener-server" && $# -lt 2 ]]; then
  PROD_PATTERN='com.svenruppert.urlshortener.api.*'
  TEST_PATTERN='junit.com.svenruppert.urlshortener.api.*'
elif [[ "$MODULE" == "urlshortener-client" && $# -lt 2 ]]; then
  PROD_PATTERN='com.svenruppert.urlshortener.client.*'
  TEST_PATTERN='junit.com.svenruppert.urlshortener.client.*'
fi

echo "==> module:     $MODULE"
echo "==> prod scope: $PROD_PATTERN"
echo "==> test scope: $TEST_PATTERN"
echo

mvn -pl "$MODULE" -am install -DskipTests -q

mvn -pl "$MODULE" org.pitest:pitest-maven:mutationCoverage \
    -Dpitest-prod-classes="$PROD_PATTERN" \
    -Dpitest-test-classes="$TEST_PATTERN"

REPORT="$MODULE/target/pit-reports/index.html"
if [[ -f "$REPORT" ]]; then
  echo
  echo "==> report: $REPORT"
  if command -v open >/dev/null 2>&1; then
    open "$REPORT"
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$REPORT"
  fi
fi
