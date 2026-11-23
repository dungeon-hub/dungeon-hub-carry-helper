#!/usr/bin/env python3
"""Generates patch notes for the current mod version.

Collects every change between the previous version bump (the last commit that
changed ``mod_version`` in ``gradle.properties``) and the target ref and injects
it into a template.

For merged pull requests, the "Patch notes" section of the description (see
``.github/pull_request_template.md``) is used. If it is empty, the title is used
instead; if it says "none", the pull request is left out. Commits pushed
directly are listed by their subject.

The template uses ``${name}`` placeholders, like the ResourcefulGradle
template injection. Available values are every entry of ``gradle.properties``
(e.g. ``${mod_version}``, ``${minecraft_version}``) plus ``${version}``,
``${previous_version}``, ``${compare_url}`` and ``${changes}``.
"""

import argparse
import json
import os
import re
import subprocess
from pathlib import Path
from string import Template

PROPERTIES_FILE = "gradle.properties"
VERSION_KEY = "mod_version"
BUMP_COMMIT = re.compile(r"^bump(ed)?\b", re.IGNORECASE)
MERGE_PR = re.compile(r"^Merge pull request #(\d+)")
PATCH_NOTES_HEADING = re.compile(r"^#+\s*patch notes\s*$", re.IGNORECASE | re.MULTILINE)
NEXT_HEADING = re.compile(r"^#+\s", re.MULTILINE)
HTML_COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout.strip()


def parse_properties(text: str) -> dict[str, str]:
    properties = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")) or "=" not in line:
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
    return properties


def properties_at(ref: str) -> dict[str, str]:
    return parse_properties(git("show", f"{ref}:{PROPERTIES_FILE}"))


def find_previous_bump(ref: str, version: str) -> str | None:
    """Returns the newest commit before ``ref`` that set ``mod_version`` to something other than ``version``."""
    candidates = git("log", "--format=%H", f"-G^{VERSION_KEY}=", ref, "--", PROPERTIES_FILE).splitlines()
    for commit in candidates:
        if properties_at(commit).get(VERSION_KEY) != version:
            return commit
    return None


def collect_commits(revision_range: str) -> list[dict[str, str]]:
    log = git("log", "--format=%H%x1f%s%x1f%b%x1e", revision_range)
    commits = []
    for entry in log.split("\x1e"):
        if not entry.strip():
            continue
        sha, subject, body = (entry.strip("\n").split("\x1f") + ["", ""])[:3]
        commits.append({"sha": sha, "subject": subject.strip(), "body": body.strip()})
    return commits


def fetch_pull_request(number: str) -> dict[str, str] | None:
    """Looks up a pull request with the GitHub CLI; returns None if it is unavailable."""
    try:
        output = subprocess.run(
            ["gh", "pr", "view", number, "--json", "number,title,body"],
            check=True, capture_output=True, text=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError):
        return None
    return json.loads(output)


def gather_changes(revision_range: str) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    pull_requests = []
    commits = []
    # Commits that belong to a merged pull request are already described by it.
    covered = set()
    for commit in collect_commits(revision_range):
        if commit["sha"] in covered:
            continue
        merge = MERGE_PR.match(commit["subject"])
        if merge:
            covered.update(git("log", "--format=%H", f"{commit['sha']}^1..{commit['sha']}^2").splitlines())
            pull_request = fetch_pull_request(merge.group(1))
            pull_requests.append(pull_request or {"number": int(merge.group(1)), "title": commit["body"], "body": ""})
        elif not commit["subject"].startswith("Merge ") and not BUMP_COMMIT.match(commit["subject"]):
            commits.append(commit)
    return pull_requests, commits


def patch_notes_section(body: str) -> str | None:
    """Returns the "Patch notes" section of a pull request description, or None if it has none."""
    body = HTML_COMMENT.sub("", body or "").replace("\r\n", "\n")
    heading = PATCH_NOTES_HEADING.search(body)
    if not heading:
        return None
    section = body[heading.end():]
    next_heading = NEXT_HEADING.search(section)
    if next_heading:
        section = section[:next_heading.start()]
    # Drop the empty bullet the template comes with.
    return "\n".join(line for line in section.splitlines() if line.strip() not in ("-", "*", "+")).strip()


def as_bullets(text: str, suffix: str = "") -> list[str]:
    """Turns text into Markdown list items, keeping indented lines (e.g. nested bullets) as they are."""
    lines = []
    for line in text.splitlines():
        if not line.strip():
            continue
        if line.startswith((" ", "\t")):
            lines.append(line.rstrip())
            continue
        if line.startswith(("- ", "* ", "+ ")):
            line = line[2:]
        lines.append(f"- {line.strip()}{suffix}")
    return lines


def render_changes(pull_requests: list[dict[str, str]], commits: list[dict[str, str]]) -> str:
    lines = []
    for pr in pull_requests:
        section = patch_notes_section(pr.get("body") or "")
        if section and section.strip("-*. ").lower() == "none":
            continue
        lines += as_bullets(section or pr["title"], f" (#{pr['number']})")
    for commit in commits:
        lines += as_bullets(commit["subject"])
    return "\n".join(lines) or "- No notable changes."


def compare_url(previous: str | None, ref: str) -> str:
    server = os.environ.get("GITHUB_SERVER_URL", "https://github.com")
    repository = os.environ.get("GITHUB_REPOSITORY")
    if not repository or not previous:
        return ""
    return f"{server}/{repository}/compare/{previous[:12]}...{git('rev-parse', ref)[:12]}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ref", default="HEAD", help="the commit to generate the patch notes for")
    parser.add_argument("--template", default="templates/patch-notes.md.template", type=Path)
    parser.add_argument("--output", default="build/patch-notes.md", type=Path)
    args = parser.parse_args()

    properties = properties_at(args.ref)
    version = properties[VERSION_KEY]
    previous = find_previous_bump(args.ref, version)
    previous_version = properties_at(previous).get(VERSION_KEY) if previous else None
    revision_range = f"{previous}..{args.ref}" if previous else args.ref

    pull_requests, commits = gather_changes(revision_range)
    changes = render_changes(pull_requests, commits)

    values = {
        **properties,
        "version": version,
        "previous_version": previous_version or "",
        "compare_url": compare_url(previous, args.ref),
        "changes": changes,
    }
    notes = Template(args.template.read_text(encoding="utf-8")).safe_substitute(values)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(notes.strip() + "\n", encoding="utf-8")
    print(f"Wrote the patch notes for {version} ({len(pull_requests)} pull requests, {len(commits)} commits) to {args.output}")


if __name__ == "__main__":
    main()
