#!/usr/bin/env bash
# Publishes one GitHub release per port: the mod jar plus a packwiz .pw.toml that points at it.
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
    pw="build/release/$label/boatqol.pw.toml"
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

**packwiz:** \`packwiz url add BoatQOL $url\`, or put [boatqol.pw.toml](https://github.com/$repo/releases/download/$tag/boatqol.pw.toml) in your pack's \`mods\` folder and run \`packwiz refresh\`.

See the [README](https://github.com/$repo#installing) for details."
}

release b1.7.3 b1.7.3 "Beta 1.7.3 (Babric)"
release bta bta8 "Better than Adventure 8 (Babric)"
release forge-1.7.10 forge-1.7.10 "Minecraft 1.7.10 (Forge)"
