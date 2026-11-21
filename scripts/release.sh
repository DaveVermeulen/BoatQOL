#!/usr/bin/env bash
# Publishes one GitHub release per port: the mod jar plus a packwiz .pw.toml that points at it.
# The .pw.toml files are also written to packwiz/<port>/ for the workflow to commit.
# Run by .github/workflows/release.yml after `./gradlew build`.
set -euo pipefail

repo="${GITHUB_REPOSITORY:?}"

prop() { grep "^$2=" "$1/gradle.properties" | cut -d= -f2-; }

release() {
    local dir="$1" label="$2" game="$3"
    local version jar tag file url hash pw
    version="$(prop "$dir" mod_version)"
    jar="$dir/build/libs/$(prop "$dir" archives_base_name)-$version.jar"
    tag="$version-$label"
    file="$(basename "$jar")"
    url="https://github.com/$repo/releases/download/$tag/$file"

    if gh release view "$tag" --repo "$repo" >/dev/null 2>&1; then
        echo "Release $tag already exists, skipping"
        return
    fi

    hash="$(sha256sum "$jar" | cut -d' ' -f1)"
    # Also kept in the repository at a fixed path per port (committed by the workflow), so
    # there is always one obvious file for each game version: packwiz/<port>/boatqol.pw.toml.
    pw="packwiz/$label/boatqol.pw.toml"
    mkdir -p "$(dirname "$pw")"
    cat > "$pw" <<TOML
name = "BoatQOL"
filename = "$file"
side = "both"

[download]
url = "$url"
hash-format = "sha256"
hash = "$hash"
TOML

    gh release create "$tag" "$jar" "$pw" --repo "$repo" --target "$GITHUB_SHA" \
        --title "BoatQOL $version for $game" \
        --notes "Modern (1.9+) boats for $game. Install on the server and every client.

**packwiz** (run in your pack folder): \`packwiz url add BoatQOL $url\`

See the [README](https://github.com/$repo#installing) for details."
}

release b1.7.3 b1.7.3 "Beta 1.7.3 (Babric)"
release bta bta8 "Better than Adventure 8 (Babric)"
release forge-1.7.10 forge-1.7.10 "Minecraft 1.7.10 (Forge)"
