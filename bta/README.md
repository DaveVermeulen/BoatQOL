# BoatQOL for Better than Adventure 8 (Babric)

Modern (1.9 to 1.12) boats for BTA 8.0.1. Behaviour follows [docs/SPEC.md](../docs/SPEC.md).

## What changes compared to the vanilla BTA boat

- **Physics:** the 1.12 boat: about 8 m/s on water, about 2 m/s on land, turning that eases in and out.
  It floats at the same height as the vanilla BTA boat.
- **Steering:** W/S and A/D as in vanilla BTA, but with the 1.12 feel. You turn exactly with the boat
  and can look at most 105° away from where it points.
- **Never breaks:** like vanilla BTA it doesn't break in crashes or falls. It also drops a boat item
  when something other than a player breaks it (vanilla drops planks and sticks then).
- **Ice:** by default the same as vanilla BTA (no extra speed). `useModernIceSlide=true` in
  `config/boatqol.properties` gives the fast 1.9+ ice slide. In multiplayer the server's setting is used.
- Kept from vanilla BTA: sneak to get out, boats placed facing where you look, flags on boats,
  boats breaking lily pads.

## Multiplayer

Install on the server and every client. The server sends a `BoatQOL:Config` custom payload on login;
a modded client answers `BoatQOL:Hello` and from then on drives its own boat, sending its position in
`BoatQOL:Move`. Vanilla BTA ignores these channels, so vanilla clients and servers keep the vanilla
BTA boat.

## Building

From the repository root: `./gradlew :bta:build`. The jar ends up in `bta/build/libs/`.
