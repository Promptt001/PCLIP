# PCLIP — Meteor Addon for “teleport-style” clipping (ViaFabricPlus → 1.6.4 servers)


## WARNING: Fall damage will be severe if you do not teleleport to an air block directly over a solid block! No fall wont help help if your target is a few blocks off the ground!


PCLIP is a small **Meteor Client addon** (Fabric) that adds one command:

> `.pclip <x> <y> <z>`

It was written for a very specific setup:

- You run a **modern Fabric client** with **Meteor Client**
- You connect to a **Minecraft 1.6.4 server** using **ViaFabricPlus** (protocol translation)
- You want long-distance clips to:
  - avoid the “diagonal move makes the server hitch” behavior
  - look like a clean teleport to the final destination

This addon intentionally stays **mixin-free** and minimal.

---

## Why this exists (the problem)

On older servers (including 1.6.4-era movement logic), a single huge move that changes both **X and Z** (a “diagonal jump”) can cause a server hitch. Even if you *think* you’re just “teleporting,” the server often treats it like a movement update and may do expensive checks over a large XZ area.

A huge move along only **X** or only **Z** usually touches a much thinner region of the world. A diagonal move can blow that area up drastically (it’s effectively a big rectangle in chunk-space), which can spike tick time.

So: **PCLIP avoids diagonal clips entirely**.

---

## What `.pclip x y z` does (movement sequence)

When you run:

```
.pclip <x> <y> <z>
```

PCLIP performs a staged sequence spread across multiple client ticks:

### Stage A — initial “travel height” VClip

PCLIP first moves you vertically to a **travel height** (nominally **Y=300**).

**Important for 1.6.4 servers:**  
Minecraft 1.6.4 worlds are effectively 0–255, so **Y=300 is not valid** on a real 1.6.4 server. PCLIP therefore **clamps** the travel height to the maximum safe height for the current world/dimension (usually ~254 on 256-height worlds).

So the intention is:

> “Travel as high as possible, up to 300.”

### Stage B — axis-only horizontal travel at travel height

Instead of one large diagonal horizontal move, PCLIP does:

1) **X-only clip** to:
- `(targetX, travelY, currentZ)`

2) **Z-only clip** to:
- `(targetX, travelY, targetZ)`

These are deliberately separated across ticks so the server sees them as distinct axis-aligned moves.

### Stage C — final VClip to the destination Y

Once the X/Z destination is reached at travel height, PCLIP performs a final vertical move to land at:

- `(targetX, targetY, targetZ)`

### Rotation

During the sequence, PCLIP rotates your view to look toward the final destination point `(x, y, z)`.

---

## Usage

Example:

```
.pclip 10000 64 -2500
```

Expected behavior:
- VClip to travel height (clamped if the server/dimension max height is < 300)
- X-only travel at travel height
- Z-only travel at travel height
- final VClip to Y=64

---

## Vehicle behavior

If you are mounted (boat, minecart, etc.), PCLIP also moves your vehicle so you do not desync.

---

## Compatibility notes (written for 1.6.4 server + ViaFabricPlus)

### What this is targeting

This addon runs on your **modern** Meteor-supported client version, while your server behaves like **1.6.4** via ViaFabricPlus translation.

So compatibility is determined by three things:
1) the client version (Meteor/Fabric/Yarn API level)
2) ViaFabricPlus translation behavior for movement packets
3) server-side movement validation rules and plugins

### Likely to work (best guess)

- Vanilla-ish servers with permissive movement handling
- Older servers without strict anti-cheat rules
- Environments where ViaFabricPlus doesn’t aggressively sanitize movement deltas

### May partially work / rubberband / fail

- Servers with anti-cheat or strict movement validation
- Servers that reject large position deltas even if axis-only
- Heavily modded servers with custom movement logic

### Other server versions: speculation

Because this is “movement abuse,” results vary enormously by server configuration:

- **1.6.4–1.17-ish (256-height worlds):**
  - travelY will clamp near ~254
  - axis-only splitting still helps vs diagonal
  - fall damage risk is higher because final vertical delta is large

- **1.18+ (expanded height):**
  - travelY=300 is usually naturally valid in many dimensions
  - still may rubberband due to stricter validation/anti-cheat prevalence

Bottom line: **No guarantee**. Many servers will cancel or punish this behavior.

---

## How PCLIP avoids “diagonal lag” on old servers

PCLIP intentionally avoids sending a single movement that changes both X and Z by a large amount in one update.

Instead it does:

- Tick N: VClip to travelY  
- Tick N+1: X-only clip  
- Tick N+2: Z-only clip  
- Tick N+3: final VClip to targetY  

The “separate tick” staging is important: many clients will only transmit the *final* position if you do multiple moves in the same tick, which defeats the axis-only strategy.

---

## Fall damage (common issue)

### Why it happens

Even if PCLIP sets `fallDistance = 0` on the client, **the server’s fall damage is authoritative**.

When you “teleport” vertically (especially from a high travelY down to a low targetY), the server may interpret that as a fall and apply damage when you land. On old protocols/servers (and via translation), this can be more pronounced.

### Practical ways to reduce it (most reliable → least reliable)

1) **Land into water**  
   If your destination is water (or you can target water), fall damage is avoided.

2) **Feather Falling boots / Protection**  
   On 1.6.4, Feather Falling exists and is one of the best “legal” mitigations.

3) **Land on fall-mitigating blocks**  
   On 1.6.x, hay bales exist (reduce fall damage). Ladders/vines/cobwebs also reduce/negate fall behavior if you catch them.

4) **Change travel height** (feature idea)  
   A lower travel height reduces the vertical delta and thus reduces fall damage risk—but it may reintroduce server lag if you travel horizontally through denser terrain.
   (This addon currently uses travelY=300 clamped.)

5) **“On-ground packet” tricks**
   Some clients attempt to reset server fall distance by forcing an “onGround” state update after teleporting. This is highly server-dependent and can be flagged by anti-cheat. PCLIP does not currently rely on this, because it is not reliable across translations and server configs.

---

## Building (Debian)

From the repository root:

```bash
cd /home/promptt/0_gits/PCLIP
chmod +x gradlew
./gradlew clean build
```

Jar output:

```text
build/libs/
```

Copy to your instance mods folder alongside Meteor and ViaFabricPlus:

```bash
cp build/libs/*.jar ~/.minecraft/mods/
```

If using MultiMC/Prism, copy into that instance’s `mods/` directory.

---

## Troubleshooting

### Startup crash mentioning `ExampleMixin` / `addon-template.mixins.json`

This means template mixins are still referenced.

Fix:
- remove `"mixins": [...]` from `src/main/resources/fabric.mod.json`
- delete any `*mixins*.json` files in `src/main/resources/`
- rebuild and replace the jar in your mods folder

### `.pclip` command not found

- confirm the addon jar is loaded (in the same mods folder as Meteor)
- confirm `fabric.mod.json` includes a `meteor` entrypoint pointing to your addon class
- confirm you’re using Meteor’s prefix (`.`) and not another chat prefix

### Rubberbanding / cancelled movement

Server is rejecting the move. Causes include anti-cheat, movement validation, or destination chunk/loading edge cases.

---

## Safety / server rules

This addon is intended for environments where you’re allowed to do this (singleplayer, your own server, testing, or servers where this is acceptable). Many servers consider this cheating.

---

## License

Add a `LICENSE` file if you plan to publish (MIT is common).
