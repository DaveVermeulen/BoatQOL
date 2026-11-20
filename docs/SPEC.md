# BoatQOL behavior spec

The target is the boat as it worked in Java Edition **1.9 to 1.12.2** (the boat
rewrite landed in 1.9; 1.13 only added underwater features such as bubble
columns). 1.12.2's `EntityBoat` is the reference, because it is the easiest
decompiled source to read and the closest in style to the old versions.

This spec is shared by all three ports:

| Port | Loader | Notes |
|------|--------|-------|
| `b1.7.3/` | Babric (Fabric loader + babric-loom-extension, biny mappings) | First target |
| `bta/` | Babric for Better Than Adventure | BTA is based on b1.7.3, so most code carries over |
| `1.7.10/` | Forge (RetroFuturaGradle) | Separate code, same behavior |

Values below are taken from 1.12.2 unless noted. "Tick" means one game tick
(20 per second).

## 1. Controls

The rider steers with movement keys, not with where they look.

Each tick the controlling rider's input is applied like this:

```
f = 0
if left:   deltaRotation -= 1
if right:  deltaRotation += 1
if right != left and !forward and !back:  f += 0.005   // slow creep while turning in place
yaw += deltaRotation
if forward: f += 0.04
if back:    f -= 0.005
motionX += sin(-yaw in radians) * f
motionZ += cos(-yaw in radians) * f
```

- `deltaRotation` is the boat's turn rate. It is damped every tick by the
  same `momentum` factor as the velocity (section 2), so turning eases in and out.
- The rider turns with the boat: when the boat's yaw changes by `deltaRotation`,
  the rider's yaw changes by the same amount.
- The rider's look direction is clamped to ±105° from the boat's yaw.
- Jump does nothing. Sneak dismounts, as before.

## 2. Physics

Every tick the boat works out its **status** from the blocks around its hitbox:

| Status | Condition |
|--------|-----------|
| `IN_WATER` | Bottom of hitbox is in water and the boat floats on the surface |
| `UNDER_WATER` | Water covers the top of the hitbox, still water |
| `UNDER_FLOWING_WATER` | Water covers the top of the hitbox, flowing water |
| `ON_LAND` | Resting on a solid block (any slipperiness above 0 under the hitbox) |
| `IN_AIR` | None of the above |

Then it updates motion:

```
gravity  = -0.04
momentum = 0.05            // default
buoyancy = 0

IN_WATER:            buoyancy = (waterLevel - posY) / height;  momentum = 0.9
UNDER_FLOWING_WATER: gravity  = -0.0007;                       momentum = 0.9
UNDER_WATER:         buoyancy = 0.01;                          momentum = 0.45
IN_AIR:              momentum = 0.9
ON_LAND:             momentum = boatGlide        // see below

motionX       *= momentum
motionZ       *= momentum
deltaRotation *= momentum
motionY       += gravity
if buoyancy > 0:
    motionY += buoyancy * 0.06153846
    motionY *= 0.75
```

Then the boat moves by its motion with normal block collision.

**boatGlide** is the average slipperiness of the blocks under the hitbox
(normal blocks 0.6, ice 0.98). That high ice value is what makes modern boats
fly across ice.

**Ice config option** "Use modern ice slide" (`useModernIceSlide`, default `false`):

- `false` (default): boats on ice behave as they did in that game version's
  vanilla boat. The old boats (b1.7.3, BTA, 1.7.10) get no extra speed from ice,
  so ice counts as a normal block (0.6) in `boatGlide`. Each port checks this
  against its own version's vanilla boat.
- `true`: ice uses its real slipperiness (0.98), giving the 1.9+ fast ice slide.

The boat must move on land (slowly), not just in water. That matches 1.9+.

## 3. Durability

- The boat **never breaks** from collisions, from landing on a block after any fall,
  or from hitting another entity. Fall distance is reset when the boat lands.
  (Note: 1.9 to 1.12 still broke boats that fell more than 3 blocks onto land.
  This mod deliberately leaves that out.)
- A player breaks it by hitting it, the same way as old boats: hits add damage
  and it breaks once damage passes 40. In creative it breaks in one hit.
- When broken it drops **one boat item**, never planks and sticks.

## 4. Riding and multiplayer

The mod must be installed on **both** the server and every client.

Modern boats are client-authoritative, and this is the model to copy:

1. The client of the player who controls the boat runs controls and physics locally,
   so the boat responds with no lag.
2. That client sends the server the boat's position and yaw each tick it changes,
   in a custom packet ("vehicle move").
3. The server checks that the move is plausible (no huge jumps; same idea as
   1.12's "moved wrongly" check). If it is, the server applies it, and it
   broadcasts the position to other players as normal entity movement.
4. Other clients only interpolate the boat, as before.
5. Boats with no rider are simulated by the server, with the same physics.

Each port decides how to register the custom packet (a mixin into the packet ID
table on Babric and BTA, Forge's `SimpleNetworkWrapper` on 1.7.10).

## 5. Port notes

### b1.7.3 (done)

- The Beta boat's bow points along `-(cos yaw, sin yaw)` (vanilla turns it to face away from
  its motion, and seats the rider 0.4 blocks toward the stern). The port keeps that convention so
  the old renderer and seat line up, and rotates the 1.12 formulas to match. A rider facing the
  bow has yaw = boat yaw + 90°.
- Float height matches the vanilla Beta boat (bottom of the hitbox at the water surface), not
  the 1.12 draft: the water checks use a hull 0.385 blocks below the Beta hitbox, which with the
  1.12 buoyancy puts the hitbox bottom where the Beta boat floats.
- Beta 1.7.3 has no creative mode, so "breaks in one hit in creative" doesn't apply.
- Sneaking makes the rider get out; the server (or singleplayer world) does it, so it works
  for riders without the mod too. A sneaking player can't board. Using the boat again also
  still gets you out, as in vanilla Beta.
- Handshake: the client puts a marker in the login packet's world seed field (unused from
  client to server). The server records who has the mod and sends those clients packet 212
  with its settings. The client steers locally only after it gets packet 212; the server
  only expects packet 211 (vehicle move) from marked clients. If the rider's client sends no
  move for 60 ticks, the server simulates the boat itself.
- A rider without the mod gets the vanilla Beta boat logic, but still with no crash breakage
  and a boat item drop.
- Packet ids: 211 (vehicle move, client to server), 212 (server config, server to client).

## 6. Out of scope

- Paddle animations and sounds.
- Two passengers (needs entity multi-passenger support the old versions don't have).
- Wood types for boats.
- Bubble columns and other 1.13+ water behavior.
