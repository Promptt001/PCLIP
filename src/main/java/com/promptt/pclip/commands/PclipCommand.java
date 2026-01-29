package com.promptt.pclip.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PclipCommand extends Command {
    // Only allow one active sequence at a time.
    private static ClipSequence active;

    public PclipCommand() {
        super("pclip", "Precision clip: vclip to Y, rotate to target, then clip in two axis-only steps. Usage: .pclip <x> <y> <z>");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("x", DoubleArgumentType.doubleArg())
            .then(argument("y", DoubleArgumentType.doubleArg())
                .then(argument("z", DoubleArgumentType.doubleArg())
                    .executes(ctx -> {
                        if (mc.player == null) return SINGLE_SUCCESS;

                        double x = ctx.getArgument("x", Double.class);
                        double y = ctx.getArgument("y", Double.class);
                        double z = ctx.getArgument("z", Double.class);

                        // Cancel any existing sequence to avoid weird interleaving.
                        if (active != null) active.cancel();

                        active = new ClipSequence(x, y, z);
                        active.start();

                        return SINGLE_SUCCESS;
                    })
                )
            )
        );
    }

    private static class ClipSequence {
        private final double targetX, targetY, targetZ;

        // We store the Z at the moment after vclip so the X-only step is truly axis-only.
        private double zAfterVclip;

        // 0 = not started, 1 = waiting for X-only tick, 2 = waiting for Z-only tick
        private int stage = 0;
        private boolean running = false;

        ClipSequence(double targetX, double targetY, double targetZ) {
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
        }

        void start() {
            if (mc.player == null) return;

            running = true;

            // Stage 0 (immediate): VClip only (Y changes, X/Z stay the same).
            vclipToY(targetY);

            // Capture Z after the vclip (should be unchanged, but we lock it in).
            zAfterVclip = mc.player.getZ();

            // Rotate to look directly at the final target after vclip.
            rotateToward(targetX, targetY, targetZ);

            // Subscribe for tick-based axis splitting.
            MeteorClient.EVENT_BUS.subscribe(this);

            // Next tick will do X-only.
            stage = 1;
        }

        void cancel() {
            if (!running) return;
            running = false;
            MeteorClient.EVENT_BUS.unsubscribe(this);
        }

        @EventHandler
        private void onTick(TickEvent.Post event) {
            if (!running) return;
            if (mc.player == null) {
                cancel();
                return;
            }

            // Stage 1 (tick): X-only move: (targetX, targetY, zAfterVclip)
            if (stage == 1) {
                axisClipX(targetX, targetY, zAfterVclip);
                stage = 2;
                return;
            }

            // Stage 2 (next tick): Z-only move: (targetX, targetY, targetZ)
            if (stage == 2) {
                axisClipZ(targetX, targetY, targetZ);
                // Done.
                cancel();
                active = null;
            }
        }

        private void vclipToY(double y) {
            if (mc.player == null) return;

            double x = mc.player.getX();
            double z = mc.player.getZ();

            if (mc.player.hasVehicle()) {
                Entity vehicle = mc.player.getVehicle();
                if (vehicle != null) vehicle.setPosition(vehicle.getX(), y, vehicle.getZ());
            }

            mc.player.setPosition(x, y, z);
        }

        private void axisClipX(double x, double y, double zFixed) {
            if (mc.player == null) return;

            if (mc.player.hasVehicle()) {
                Entity vehicle = mc.player.getVehicle();
                if (vehicle != null) vehicle.setPosition(x, y, zFixed);
            }

            mc.player.setPosition(x, y, zFixed);
        }

        private void axisClipZ(double xFixed, double y, double z) {
            if (mc.player == null) return;

            if (mc.player.hasVehicle()) {
                Entity vehicle = mc.player.getVehicle();
                if (vehicle != null) vehicle.setPosition(xFixed, y, z);
            }

            mc.player.setPosition(xFixed, y, z);
        }

        private void rotateToward(double x, double y, double z) {
            if (mc.player == null) return;

            // Use eye position so pitch is “look directly at” the point.
            Vec3d from = mc.player.getEyePos();
            double dx = x - from.x;
            double dy = y - from.y;
            double dz = z - from.z;

            double horiz = Math.sqrt(dx * dx + dz * dz);

            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            yaw = MathHelper.wrapDegrees(yaw);

            float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
            pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);

            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
        }
    }
}
