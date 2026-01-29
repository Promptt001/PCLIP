# PCLIP (Meteor Addon)

A small Meteor Client addon that adds a precision clip command:

- **`.pclip <x> <z>`**  
  Rotates your player to face the target X/Z coordinate and then **clips you exactly to that X/Z**, keeping your current Y (height).  
  This avoids long-distance “crosshair drift” and removes the need to manually calculate distances.

> This addon is intentionally minimal: one command, no GUI, no extra modules.

---

## Features

- ✅ **Exact X/Z clip** (no forward-distance approximation)
- ✅ **Auto-rotate yaw** to face the destination before clipping
- ✅ Keeps **current Y** (hclip-style horizontal clip)
- ✅ Moves your **vehicle too** if you’re mounted (boat, minecart, etc.)
- ✅ Lightweight: no mixins, no extra dependencies beyond Meteor/Fabric

---

## Requirements

- **Fabric Loader** (matching your Minecraft version)
- **Meteor Client** (same MC version)
- **Java**: use the Java version recommended for your Minecraft version (commonly Java 21 for modern versions)

---

## Installation (Users)

1. Build the addon jar (or download a release jar if you publish one).
2. Put the jar in your instance’s `mods` folder **alongside Meteor Client**.
3. Launch Minecraft with Fabric + Meteor.

---

## Usage

In chat (Meteor command prefix is a dot):

```text
.pclip 1000 -2500
