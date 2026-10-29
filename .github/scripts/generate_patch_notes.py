#!/usr/bin/env python3
"""Generates patch notes for the current mod version.

Collects every change between the previous version bump (the last commit that
changed ``mod_version`` in ``gradle.properties``) and the target ref, optionally
lets Claude summarize them, and injects the result into a template.

The template uses ``${name}`` placeholders, like the ResourcefulGradle
template injection. Available values are every entry of ``gradle.properties``
(e.g. ``${mod_version}``, ``${minecraft_version}``) plus ``${version}``,
``${previous_version}``, ``${compare_url}`` and ``${changes}``.

Summarizing with Claude requires the ``anthropic`` package and an
``ANTHROPIC_API_KEY``. Without them, the notes fall back to a plain list of the
merged pull requests and commits.
"""

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from string import Template

PROPERTIES_FILE = "gradle.properties"
VERSION_KEY = "mod_version"
BUMP_COMMIT = re.compile(r"^bump(ed)?\b", re.IGNORECASE)
MERGE_PR = re.compile(r"^Merge pull request #(\d+)")

SYSTEM_PROMPT = """You write patch notes for a Minecraft mod. They are published on \
the mod's release page and read by players, not developers.

Write a short Markdown list of the changes, grouped under the headings \
"### Added", "### Changed" and "### Fixed" (leave out empty groups). Each \
bullet is one sentence describing what changed for the player. Merge related \
commits into one bullet, leave out purely internal changes (refactors, tests, \
CI, version bumps) unless they affect players, and do not invent changes that \
the input does not mention. Mention a pull request as (#123) when a bullet is \
based on one. Reply with the list only, without a title or closing remarks."""


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


def plain_changes(pull_requests: list[dict[str, str]], commits: list[dict[str, str]]) -> str:
    lines = [f"- {pr['title']} (#{pr['number']})" for pr in pull_requests]
    lines += [f"- {commit['subject']}" for commit in commits]
    return "\n".join(lines) or "- No notable changes."


def describe_changes(pull_requests: list[dict[str, str]], commits: list[dict[str, str]]) -> str:
    parts = []
    for pr in pull_requests:
        parts.append(f"Pull request #{pr['number']}: {pr['title']}\n{pr.get('body') or ''}".strip())
    for commit in commits:
        parts.append(f"Commit {commit['sha'][:7]}: {commit['subject']}\n{commit['body']}".strip())
    return "\n\n".join(parts)


def summarize_with_claude(changes: str, version: str, previous_version: str | None) -> str | None:
    if not os.environ.get("ANTHROPIC_API_KEY"):
        print("ANTHROPIC_API_KEY is not set, using the plain change list.", file=sys.stderr)
        return None
    try:
        import anthropic
    except ImportError:
        print("The anthropic package is not installed, using the plain change list.", file=sys.stderr)
        return None

    client = anthropic.Anthropic()
    header = f"Changes from version {previous_version} to {version}:" if previous_version else f"Changes in version {version}:"
    try:
        response = client.beta.messages.create(
            model=os.environ.get("PATCH_NOTES_MODEL", "claude-opus-5-5"),
            max_tokens=16000,
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
            output_config={"effort": "medium"},
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": f"{header}\n\n{changes}"}],
        )
    except anthropic.APIError as error:
        print(f"Summarizing with Claude failed ({error}), using the plain change list.", file=sys.stderr)
        return None

    if response.stop_reason != "end_turn":
        print(f"Claude stopped with '{response.stop_reason}', using the plain change list.", file=sys.stderr)
        return None
    text = "".join(block.text for block in response.content if block.type == "text").strip()
    return text or None


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
    parser.add_argument("--no-ai", action="store_true", help="skip summarizing with Claude")
    args = parser.parse_args()

    properties = properties_at(args.ref)
    version = properties[VERSION_KEY]
    previous = find_previous_bump(args.ref, version)
    previous_version = properties_at(previous).get(VERSION_KEY) if previous else None
    revision_range = f"{previous}..{args.ref}" if previous else args.ref

    pull_requests, commits = gather_changes(revision_range)
    changes = None
    if not args.no_ai and (pull_requests or commits):
        changes = summarize_with_claude(describe_changes(pull_requests, commits), version, previous_version)
    if changes is None:
        changes = plain_changes(pull_requests, commits)

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
