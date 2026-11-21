# BoatQOL for Minecraft 1.7.10 (Forge)

Modern (1.9 to 1.12) boats for Forge 1.7.10 (10.13.4.1614). Behaviour follows [docs/SPEC.md](../docs/SPEC.md).

## What changes compared to the vanilla 1.7.10 boat

- **Steering:** W/S drives and A/D turns, instead of the boat going where you look. You turn with
  the boat and can look at most 105° away from where it points.
- **Physics:** the 1.12 boat: about 8 m/s on water, slow on land, turning that eases in and out.
  It floats at the same height as the vanilla boat.
- **Never breaks** in crashes or falls. Breaking it by hand still drops a boat item.
- **Placement:** a placed boat faces where you look.
- **Ice:** by default the same as vanilla 1.7.10 (no extra speed). `useModernIceSlide=true` in
  `config/boatqol.cfg` gives the fast 1.9+ ice slide. In multiplayer the server's setting is used.
- Kept from vanilla: sneak to get out, the boat model, breaking lily pads and snow layers.

No core mod or mixins are needed. Existing boats in a world become modern boats, and they stay
normal boats if the mod is removed.

## Multiplayer

Install on the server and every client. The rider's client drives its own boat and sends the
position to the server, which checks it and passes it on to the other players.

## Building

From the repository root: `./gradlew :forge-1.7.10:build`. The jar ends up in `forge-1.7.10/build/libs/`.
