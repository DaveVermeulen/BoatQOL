# BoatQOL for Beta 1.7.3 (Babric)

Modern (1.9 to 1.12) boats for Minecraft Beta 1.7.3 on [Babric](https://babric.github.io/).
Behaviour follows [docs/SPEC.md](../docs/SPEC.md).

## Install

1. Set up a Babric b1.7.3 instance (Fabric Loader 0.16 or newer) on the client, and on the server if you play multiplayer.
2. Put `boatqol-b1.7.3-<version>.jar` in the `mods` folder.

In multiplayer, install it on the server and on every client.

## What changes

- **Getting out:** sneak (shift) to get out, like 1.9+. You can't get in while sneaking.
- **Placing:** a placed boat faces the way you're looking.
- **Steering:** W/S move forward and back, A/D turn the boat. Looking around no longer steers.
  You turn with the boat and can look at most 105° away from where it points.
- **Physics:** the 1.12 boat: floats on the surface, about 8 m/s on water, slow (about 2 m/s) on land.
- **Never breaks:** crashing into walls, landing after a fall, or bumping entities never breaks it.
  Punching it still breaks it (same number of hits as before) and drops **a boat**, not planks and sticks.
- **Ice:** by default ice is no faster than other blocks, like the Beta boat. Set
  `useModernIceSlide=true` in `config/boatqol.properties` for the fast 1.9+ ice slide.
  In multiplayer the server's setting is used.

## Multiplayer details

- The rider's own client drives the boat (no input lag) and sends the position to the server
  in a custom packet (id 211). The server ignores moves of more than 10 blocks per packet.
- A client with the mod marks its login packet in a field vanilla never uses, and the server
  answers with packet 212 (the server's settings). Neither side sends a custom packet until it
  knows the other side has the mod, so:
  - a modded client on a vanilla server just gets vanilla boats, and
  - a vanilla client on a modded server rides a vanilla-style boat that the server drives
    (still unbreakable, still drops a boat item).

## Building

See [Developing](../README.md#developing) in the main README. From the repository root:

```
./gradlew :b1.7.3:build
```

The jar ends up in `b1.7.3/build/libs/`.
