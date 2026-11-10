#!/usr/bin/env bash
# Pre-fetches the Gradle distribution that gradle/wrapper/gradle-wrapper.properties asks for, with retries and a
# SHA-256 check, and puts it where the Gradle wrapper looks for it. The wrapper then finds the zip already there and
# downloads nothing.
#
# Why (D-042): the wrapper downloads Gradle from GitHub Releases on first use, with a single attempt. On 2026-11-09 that
# download failed on the GitHub runner three times in a row (java.io.FileNotFoundException on the gradle-distributions
# URL) and the whole installer build died before reaching any of Beatland's code.
#
# Safe by design:
#  - the zip is only used if its SHA-256 equals `distributionSha256Sum` from the wrapper properties (the wrapper itself
#    does not verify a zip that is already in place, so this script must);
#  - if anything goes wrong it only prints a warning and exits 0: the wrapper then does its normal download, so this
#    script can never make a build fail that would have passed without it.
#
# Run from the repository root:  bash scripts/beatland/prefetch-gradle.sh
# BEATLAND_GRADLE_URL (optional) overrides the download URL; the wrapper's distributionUrl still decides the install path.

set -u

props="gradle/wrapper/gradle-wrapper.properties"
warn() { echo "::warning title=Gradle prefetch::$*"; }

[ -f "$props" ] || { warn "$props not found; leaving the download to the Gradle wrapper"; exit 0; }

url="$(sed -n 's/^distributionUrl=//p' "$props" | tr -d '\r' | sed 's#\\:#:#g')"
sha="$(sed -n 's/^distributionSha256Sum=//p' "$props" | tr -d '\r')"
if [ -z "$url" ] || [ -z "$sha" ]; then
  warn "distributionUrl or distributionSha256Sum is missing; leaving the download to the Gradle wrapper"
  exit 0
fi

name="$(basename "$url" .zip)" # e.g. gradle-9.8.1-bin
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# The wrapper installs into <GRADLE_USER_HOME>/wrapper/dists/<name>/<hash>/, where <hash> is the MD5 of the
# distributionUrl written in base 36 (what org.gradle.wrapper.PathAssembler does). Same computation, in Java.
cat > "$work/GradleDistHash.java" <<'EOF'
public class GradleDistHash {
    public static void main(String[] args) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        System.out.println(new java.math.BigInteger(1, md.digest(args[0].getBytes())).toString(36));
    }
}
EOF
hash="$(java "$work/GradleDistHash.java" "$url" | tr -d '\r')"
if [ -z "$hash" ]; then
  warn "could not compute the wrapper's distribution hash; leaving the download to the Gradle wrapper"
  exit 0
fi

dest="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/$name/$hash"
echo "Gradle distribution: $name (wrapper hash $hash)"

if [ -f "$dest/$name.zip.ok" ]; then
  echo "Already installed in $dest; nothing to download."
  exit 0
fi

source_url="${BEATLAND_GRADLE_URL:-$url}"
attempts=8
for attempt in $(seq 1 "$attempts"); do
  echo "Downloading $source_url (attempt $attempt of $attempts)"
  if curl -fSL --connect-timeout 30 --max-time 600 -o "$work/$name.zip" "$source_url"; then
    actual="$(sha256sum "$work/$name.zip" | awk '{print $1}')"
    if [ "$actual" = "$sha" ]; then
      mkdir -p "$dest"
      mv "$work/$name.zip" "$dest/$name.zip"
      echo "Gradle distribution verified (SHA-256 $sha) and placed in $dest"
      exit 0
    fi
    echo "Checksum mismatch: expected $sha, got $actual"
  fi
  rm -f "$work/$name.zip"
  if [ "$attempt" -lt "$attempts" ]; then
    echo "Retrying in $((attempt * 10)) s..."
    sleep $((attempt * 10))
  fi
done

warn "could not fetch a verified $name.zip after $attempts attempts; the Gradle wrapper will try its own download"
exit 0
