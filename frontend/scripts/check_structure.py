#!/usr/bin/env python3
"""Detect drift-prone files and tracked local/generated files in the frontend.

Two checks run against the repository:

1. Forbidden drift-prone files (``temp_*``, ``*_old.*``, ``*.bak`` ...) anywhere
   in the working tree.
2. Local or generated paths (``dist/``, ``node_modules/``, ``*.local``, the
   ``harness-starter-kit/`` reference clone ...) that were accidentally committed
   to git.

Rules live in ``.harness/structure-rules.json`` so they stay data-driven.
"""

from __future__ import annotations

import fnmatch
import json
import subprocess
import sys
from pathlib import Path


DEFAULT_RULES = {
    "forbidden_patterns": [
        "**/temp_*",
        "**/*_new.*",
        "**/*_old.*",
        "**/*_backup.*",
        "**/*_fix.*",
        "**/*.bak",
    ],
    "generated_paths": [
        "dist/",
        "node_modules/",
        "coverage/",
        ".vite/",
        "harness-starter-kit/",
        "*.local",
        "*.tsbuildinfo",
    ],
    "ignored_directories": [
        ".git",
        ".idea",
        "node_modules",
        "dist",
        "coverage",
        ".vite",
        "harness-starter-kit",
    ],
}


def repo_root() -> Path:
    current = Path(__file__).resolve()
    if current.parent.name == "scripts":
        return current.parents[1]
    return Path.cwd()


def load_rules(root: Path) -> dict[str, list[str]]:
    rules_path = root / ".harness" / "structure-rules.json"
    if not rules_path.exists():
        return DEFAULT_RULES
    return json.loads(rules_path.read_text(encoding="utf-8"))


def is_ignored(path: Path, ignored_directories: set[str]) -> bool:
    return any(part in ignored_directories for part in path.parts)


def check_forbidden(root: Path, rules: dict[str, list[str]]) -> list[str]:
    ignored_directories = set(rules.get("ignored_directories", []))
    violations: set[Path] = set()
    for pattern in rules.get("forbidden_patterns", []):
        for path in root.glob(pattern):
            rel = path.relative_to(root)
            if path.is_file() and not is_ignored(rel, ignored_directories):
                violations.add(rel)
    return [f"Forbidden drift-prone file: {rel}" for rel in sorted(violations)]


def tracked_files(root: Path) -> list[str]:
    try:
        result = subprocess.run(
            ["git", "ls-files"],
            cwd=root,
            check=True,
            capture_output=True,
            text=True,
        )
    except (OSError, subprocess.CalledProcessError):
        return []
    return [line for line in result.stdout.splitlines() if line]


def matches_generated(rel_path: str, pattern: str) -> bool:
    if pattern.endswith("/"):
        prefix = pattern.rstrip("/")
        return rel_path == prefix or rel_path.startswith(prefix + "/")
    name = rel_path.rsplit("/", 1)[-1]
    return fnmatch.fnmatch(rel_path, pattern) or fnmatch.fnmatch(name, pattern)


def check_tracked_generated(root: Path, rules: dict[str, list[str]]) -> list[str]:
    generated = rules.get("generated_paths", [])
    if not generated:
        return []
    violations = [
        f"Tracked local/generated file should not be committed: {rel_path}"
        for rel_path in tracked_files(root)
        if any(matches_generated(rel_path, pattern) for pattern in generated)
    ]
    return sorted(violations)


def main() -> int:
    root = repo_root()
    rules = load_rules(root)
    problems = check_forbidden(root, rules) + check_tracked_generated(root, rules)
    for problem in problems:
        print(problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
