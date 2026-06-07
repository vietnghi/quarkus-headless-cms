#!/bin/bash
# =============================================================================
# Sample Content Seed Script
# =============================================================================
# Seeds the CMS database with sample content types, components, and demo data.
#
# Usage:
#   ./scripts/seed.sh                      # Seed with default dev profile
#   ./scripts/seed.sh -Pprod                # Seed with production profile
#   ./scripts/seed.sh --skip-schemas        # Seed data only (skip schema registration)
#   ./scripts/seed.sh --clean               # Drop and re-seed everything
#
# Prerequisites:
#   - Maven 3.9+
#   - JDK 17+
#   - PostgreSQL (or H2 for dev mode)
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
MODULE_DIR="$PROJECT_DIR"

# Defaults
MAVEN_OPTS="${MAVEN_OPTS:--Xmx512m}"
SKIP_SCHEMAS=false
CLEAN=false
MAVEN_PROFILE="dev"

# Parse flags
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-schemas) SKIP_SCHEMAS=true; shift ;;
    --clean) CLEAN=true; shift ;;
    -P*) MAVEN_PROFILE="${1#-P}"; shift ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

echo "============================================"
echo "CMS Sample Content Seed Script"
echo "============================================"
echo "Profile:      $MAVEN_PROFILE"
echo "Skip Schemas: $SKIP_SCHEMAS"
echo "Clean:        $CLEAN"
echo "============================================"

cd "$PROJECT_DIR"

if [ "$CLEAN" = true ]; then
  echo "Cleaning database..."
  mvn quarkus:dev -pl . -Dquarkus.args="--clean-db" &
  CLEAN_PID=$!
  sleep 5
  kill $CLEAN_PID 2>/dev/null || true
fi

# Build the sample content module (if needed)
echo "Building sample-content module..."
mvn compile -pl . -am -q 2>/dev/null || mvn compile -pl . -am

# Set system properties for the seeder
export CMS_SAMPLE_CONTENT_ENABLED=true

if [ "$SKIP_SCHEMAS" = true ]; then
  echo "Skipping schema registration (schemas already registered)."
  echo "Only seeding data entries..."
fi

echo ""
echo "============================================"
echo "To seed via Maven (recommended):"
echo "============================================"
echo ""
echo "  cd $PROJECT_DIR"
echo "  mvn quarkus:dev -P$MAVEN_PROFILE"
echo ""
echo "The seeder runs automatically on startup when cms.sample-content.enabled=true"
echo "(which is the default). The seed is idempotent — it only runs once."
echo ""
echo "============================================"
echo "To seed via REST API (after app is running):"
echo "============================================"
echo ""
echo "  # Register schemas"
echo "  for schema in schemas/*.json; do"
echo "    curl -X POST http://localhost:8080/_admin/content-types \\"
echo "      -H \"Content-Type: application/json\" \\"
echo "      -d @\$schema"
echo "  done"
echo ""
echo "  # Import seed data"
echo "  curl -X POST http://localhost:8080/_admin/import \\"
echo "    -H \"Content-Type: application/json\" \\"
echo "    -d '{\"contentType\":\"api::author.author\",\"entries\":@seed-data/authors.json}'"
echo ""
echo "============================================"
