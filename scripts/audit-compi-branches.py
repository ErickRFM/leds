#!/usr/bin/env python3
"""Read-only ancestry/patch inventory of every origin branch; never merges/deletes refs."""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True,
                                   encoding="utf-8", errors="replace").strip()


def patch_ids(*args):
    diff = subprocess.run(["git", *args], cwd=ROOT, stdout=subprocess.PIPE,
                          check=True).stdout
    result = subprocess.run(["git", "patch-id", "--stable"], cwd=ROOT, input=diff,
                            stdout=subprocess.PIPE, check=True).stdout.decode("utf-8")
    return [line.split() for line in result.splitlines()]


def audit(base, ref, main_patches):
    behind, ahead = map(int, git("rev-list", "--left-right", "--count",
                                 f"{base}...{ref}").split())
    cherry = git("cherry", base, ref).splitlines() if ahead else []
    equivalent = sum(line.startswith("-") for line in cherry)
    unmatched = sum(line.startswith("+") for line in cherry)
    same_tree = git("rev-parse", f"{base}^{{tree}}") == git("rev-parse", f"{ref}^{{tree}}")
    squash = []
    if ahead and not same_tree:
        merge_base = git("merge-base", base, ref)
        aggregate = patch_ids("diff", merge_base, ref)
        if aggregate:
            squash = main_patches.get(aggregate[0][0], [])
    if not ahead:
        category = "A: ancestor/included"
    elif same_tree:
        category = "T: identical tip tree"
    elif squash:
        category = "S: aggregate patch matches base-history commit"
    elif not unmatched and equivalent:
        category = "P: patch-equivalent nonmerge series"
    elif equivalent:
        category = "M: mixed; inspect unmatched patches"
    else:
        category = "D: divergent; semantic review needed"
    return dict(ref=ref, sha=git("rev-parse", ref), behind=behind, ahead=ahead,
                equivalent=equivalent, unmatched=unmatched, category=category,
                squash=squash,
                unmatched_shas=[line[2:] for line in cherry if line.startswith("+")])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="origin/main")
    parser.add_argument("--output", type=Path,
                        default=ROOT / "docs/audits/COMPI_BRANCH_CONSOLIDATION_2026-10-23.md")
    options = parser.parse_args()
    base = git("rev-parse", options.base)
    subjects = dict(line.split("\t", 1) for line in
                    git("log", "--all", "--format=%H%x09%s").splitlines())
    refs = [ref.removeprefix("refs/remotes/") for ref in
            git("for-each-ref", "--format=%(refname)", "refs/remotes/origin").splitlines()
            if ref != "refs/remotes/origin/HEAD"]
    main_patches = {}
    for patch_id, commit in patch_ids("log", "--no-merges", "-p", base):
        main_patches.setdefault(patch_id, []).append(commit)
    with ThreadPoolExecutor(max_workers=4) as pool:
        rows = list(pool.map(lambda ref: audit(base, ref, main_patches), refs))
    counts = Counter(row["category"] for row in rows)
    lines = ["# Compi branch consolidation inventory — 2026-10-23", "",
             f"Generated UTC: {datetime.now(timezone.utc).isoformat(timespec='seconds')}", "",
             f"Pinned comparison: `{options.base}` = `{base}`. Remote branch tips are local fetched refs; this script does not fetch or mutate Git.", "",
             f"Scope: **{len(rows)} origin branches** (including origin/main, excluding symbolic origin/HEAD).", "",
             "## Method and interpretation", "",
             "Each branch is checked with `git rev-list --left-right --count BASE...TIP`, `git cherry BASE TIP`, exact tip-tree comparison, and `git patch-id --stable` of the aggregate merge-base-to-tip diff against every nonmerge base-history commit. Left/right counts include merges; cherry counts exclude merge commits. All comparisons use the pinned SHA above, so concurrent workspace edits cannot change this inventory.", "",
             "- A: the tip is an ancestor of the comparison commit (right count zero). Every reachable branch commit is included in history; later commits can still supersede its behavior.",
             "- T: an unmerged tip has exactly the same tracked tree as the comparison commit.",
             "- S: the aggregate merge-base-to-tip patch has the same stable patch ID as a nonmerge commit already in base history (usually a squash merge). The matched commit SHA is recorded. Later changes may supersede that patch; stable IDs ignore whitespace.",
             "- P: every nonmerge commit reported by cherry has an equivalent patch in base history. This does **not** prove merge resolutions, aggregate squash equivalence, or present-day feature preservation.",
             "- M: some individual patches are equivalent and others unmatched. Unmatched patches are review candidates, **not proof of missing functionality**.",
             "- D: no equivalent nonmerge patches were found in the branch-only series. Squashing, rebasing with edits, manual integrations, and superseded implementations can all produce this result. Targeted source review is required.", "",
             "Individual patch IDs do not detect general semantic equivalence or many-commit squashes; aggregate patch checks recover exact squash matches, but amended or partial squashes can remain unmatched. No branch is classified as safe to delete, and no bulk merge is recommended.", "",
             "## Counts", ""]
    lines += [f"- {key}: **{value}**" for key, value in sorted(counts.items())]
    lines += ["", "## Full branch inventory", "",
              "`Base-only` / `tip-only` are ancestry counts; `−` / `+` are cherry-equivalent / unmatched nonmerge counts.", "",
              "| Branch | Exact tip SHA | Base-only | Tip-only | − | + | Classification | Aggregate matching commit(s) |",
              "|---|---|---:|---:|---:|---:|---|---|"]
    for row in rows:
        matched = ", ".join(f"`{sha}`" for sha in row["squash"])
        lines.append(f"| `{row['ref']}` | `{row['sha']}` | {row['behind']} | {row['ahead']} | {row['equivalent']} | {row['unmatched']} | {row['category']} | {matched} |")
    lines += ["", "## Unmatched patch details", "",
              "For branches with unmatched patches, these are all cherry `+` commits (SHA and subject); ancestral branches have none. They remain candidates until checked against current source and later integrations.", ""]
    for row in rows:
        if row["unmatched_shas"]:
            lines += [f"### {row['ref']}", ""]
            for sha in row["unmatched_shas"]:
                lines.append(f"- `{sha}` {subjects.get(sha, '(subject unavailable)')}")
            lines.append("")
    options.output.parent.mkdir(parents=True, exist_ok=True)
    # Keep the separately maintained semantic review when refreshing the
    # mechanical inventory against another pinned base.
    review_marker = "## Targeted source review"
    if options.output.exists():
        previous = options.output.read_text(encoding="utf-8")
        if review_marker in previous:
            lines += [review_marker + previous.split(review_marker, 1)[1]]
    options.output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {options.output}; pinned base {base}; branches={len(rows)}")
    for category, count in sorted(counts.items()):
        print(f"{category}: {count}")


if __name__ == "__main__":
    main()
