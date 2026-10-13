"""Finish a draft v0.40 release over the official REST path when the high level CLI hits a GraphQL EOF.

Mirrors what publish_release.py does after creating the draft: upload every asset, read the release
back and verify size plus digest, then open it as the latest release.
"""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

REPO = "h516df/walkpaint"
TAG = "v0.40"
DIST = Path(r"D:\Worktrees\WalkPaint\dsh-v039-fog\dist\v0.40")


def gh(*args, check=True):
    proc = subprocess.run(["gh", *args], capture_output=True, text=True)
    if check and proc.returncode != 0:
        raise SystemExit("gh failed: %s\n%s\n%s" % (" ".join(args), proc.stdout[-500:], proc.stderr[-500:]))
    return proc.stdout


def release():
    # A draft is invisible to /releases/tags/{tag}; it only shows up in the authenticated list.
    listed = json.loads(gh("api", "repos/%s/releases?per_page=20" % REPO))
    for item in listed:
        if item["tag_name"] == TAG:
            return item
    raise SystemExit("no release with tag %s yet" % TAG)


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


current = release()
release_id = current["id"]
print("draft release id:", release_id, "draft:", current["draft"], flush=True)

assets = []
for name in ("WalkPaint-v0.40-arm64.apk", "WalkPaint-v0.40-arm64-debug.apk",
             "WalkPaint-v0.40-release-notes.json", "WalkPaint-v0.40-SHA256.txt"):
    path = DIST / name
    if not path.is_file():
        raise SystemExit("missing prepared asset: %s" % path)
    assets.append(path)

have = {a["name"] for a in current["assets"]}
for path in assets:
    if path.name in have:
        print("already uploaded:", path.name, flush=True)
        continue
    print("uploading:", path.name, path.stat().st_size, "bytes", flush=True)
    gh("api", "--method", "POST",
       "-H", "Content-Type: application/octet-stream",
       "https://uploads.github.com/repos/%s/releases/%d/assets?name=%s" % (REPO, release_id, path.name),
       "--input", str(path))

remote = release()
expected = {path.name: (path.stat().st_size, sha256(path)) for path in assets}
for asset in remote["assets"]:
    want = expected.get(asset["name"])
    if not want:
        print("unexpected asset in draft:", asset["name"], flush=True)
        continue
    size, digest = want
    ok = asset["state"] == "uploaded" and asset["size"] == size and asset.get("digest") == "sha256:" + digest
    print("%-40s state=%s size=%s digest_match=%s" % (asset["name"], asset["state"], asset["size"],
                                                      asset.get("digest") == "sha256:" + digest), flush=True)
    if not ok:
        raise SystemExit("asset verification failed, leaving the release as a draft")

missing = set(expected) - {a["name"] for a in remote["assets"]}
if missing:
    raise SystemExit("draft is missing assets: %s" % sorted(missing))

receipt = {"tag": TAG, "target": gh("api", "repos/%s/git/ref/tags/%s" % (REPO, TAG), "--jq", ".object.sha").strip(),
           "release_id": release_id, "assets": remote["assets"], "draft_verified": True}
(DIST / "publication.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")

payload = DIST / "publish-release.json"
payload.write_text(json.dumps({"draft": False, "make_latest": "true"}), encoding="utf-8")
final = json.loads(gh("api", "--method", "PATCH", "repos/%s/releases/%d" % (REPO, release_id),
                      "--input", str(payload)))
if final["draft"] or not final.get("published_at"):
    raise SystemExit("release state not confirmed; still a draft")
print("published:", final["tag_name"], final["published_at"], flush=True)
