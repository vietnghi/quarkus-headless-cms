#!/usr/bin/env python3
"""Migrate module POMs from H2 to SQLite: replace jdbc-h2 with sqlite-jdbc + hibernate-community-dialects."""

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

# Modules that have @QuarkusTest and need SQLite
MODULES = [
    "cms-admin-api", "cms-audit", "cms-auth", "cms-core",
    "cms-custom-fields", "cms-draft-publish", "cms-graphql",
    "cms-i18n", "cms-media", "cms-plugin", "cms-rest-api",
    "cms-review", "cms-sample-content", "cms-webhooks",
    "deployment", "example", "integration-tests",
]

def find_dep_block(text: str, artifact_id: str) -> tuple[int, int] | None:
    """Find a complete <dependency> block containing the given artifactId.
    Returns (start, end) byte offsets, or None."""
    pattern = re.compile(
        r'(<dependency>\s*(?:\s*<!--.*?-->\s*)?'
        r'(?:<groupid>[^<]+</groupid>\s*)?'
        rf'<artifactId>{re.escape(artifact_id)}</artifactId>'
        r'.*?</dependency>)',
        re.DOTALL | re.IGNORECASE
    )
    m = pattern.search(text)
    if m:
        return (m.start(1), m.end(1))
    return None


def replace_h2_in_pom(pom_path: Path) -> bool:
    """Replace quarkus-jdbc-h2 (test) with sqlite-jdbc + hibernate-community-dialects.
    Remove quarkus-jdbc-h2-deployment (test) entirely.
    Returns True if changes were made."""
    original = pom_path.read_text(encoding="utf-8")
    text = original
    changed = False

    # 1. Replace quarkus-jdbc-h2 test dep with sqlite-jdbc + hibernate-community-dialects
    h2_block = find_dep_block(text, "quarkus-jdbc-h2")
    if h2_block:
        start, end = h2_block
        # Detect if this is test scope
        block_text = text[start:end]
        if '<scope>test</scope>' in block_text or 'test' in block_text:
            sqlite_block = """\
        <!-- SQLite for testing -->
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.hibernate.orm</groupId>
            <artifactId>hibernate-community-dialects</artifactId>
            <scope>test</scope>
        </dependency>"""
            text = text[:start] + sqlite_block + text[end:]
            changed = True
            print(f"  Replaced quarkus-jdbc-h2 in {pom_path.name}")

    # 2. Remove quarkus-jdbc-h2-deployment test dep entirely
    h2dep_block = find_dep_block(text, "quarkus-jdbc-h2-deployment")
    if h2dep_block:
        start, end = h2dep_block
        block_text = text[start:end]
        if '<scope>test</scope>' in block_text or '<version>' in block_text:
            # Remove the comment before it if any
            before = text[:start].rstrip()
            # Remove trailing blank lines before the block
            while before.endswith('\n\n') or before.endswith('\n\n\n'):
                before = before.rstrip('\n')
                if before.endswith('\n'):
                    before = before[:-1]
            # Also remove any comment immediately before
            comment_before = re.search(r'(\s*<!--.*?-->\s*)$', before, re.DOTALL)
            if comment_before:
                before = before[:comment_before.start()]
            text = before + '\n' + text[end:].lstrip('\n')
            changed = True
            print(f"  Removed quarkus-jdbc-h2-deployment from {pom_path.name}")

    if changed:
        pom_path.write_text(text, encoding="utf-8")
    return changed


def main():
    changed_count = 0
    for module in MODULES:
        pom = REPO / module / "pom.xml"
        if not pom.exists():
            print(f"  Skipping {module} (no pom.xml)")
            continue
        if replace_h2_in_pom(pom):
            changed_count += 1
    print(f"\n{changed_count} POMs updated")
    return 0 if changed_count > 0 else 1


if __name__ == "__main__":
    sys.exit(main())
