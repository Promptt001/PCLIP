# PCLIP (Meteor Addon)

A small Meteor Client addon that adds a precision clip command designed to avoid server hitches caused by large diagonal moves. This was designed for use on older servers vulnerable to cliping exploits.

## Command

### `.pclip <x> <y> <z>`

Does a 3-stage movement:

1) **VClip to Y**  
   Moves only vertically to the target Y (X/Z unchanged).

2) **Rotate to face the destination**  
   Sets yaw/pitch to look directly at the target point `(x, y, z)`.

3) **Axis-only horizontal movement (split across ticks)**  
   Instead of one big diagonal move, it moves in two thin “lines”:
   - Tick 1: move **X-only** to `(x, y, currentZ)`
   - Tick 2: move **Z-only** to `(x, y, z)`

This avoids the server receiving a single massive diagonal delta, which can cause chunk/collision lookups across a large XZ area and hitch the tick loop.

## Example

```text
.pclip 10000 64 -2500
