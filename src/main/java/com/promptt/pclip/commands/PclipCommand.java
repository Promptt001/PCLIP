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

import java.lang.reflect.Method;

public class PclipCommand extends Command {
    private static ClipSequence active;
    private static final double TRAVEL_Y_REQUESTED = 300.0;

    public PclipCommand() {
        super("pclip", "Vclip to travelY (300), axis-split hclips, then final vclip. Usage: .pclip <x> <y> <z>");
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

        // 1 = X-only at travelY, 2 = Z-only at travelY, 3 = final VClip to targetY
        private int stage = 0;
        private boolean running = false;

        private double travelY;
        private double zAfterTravel;

        ClipSequence(double targetX, double targetY, double targetZ) {
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
        }

        void start() {
            if (mc.player == null) return;

            running = true;

            travelY = getSafeTravelY();

            // Initial VClip to travelY (X/Z unchanged).
            vclipToY(travelY);
            zAfterTravel = mc.player.getZ();

            // Rotate to look at final destination point.
            rotateToward(targetX, targetY, targetZ);

            MeteorClient.EVENT_BUS.subscribe(this);
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

            if (stage == 1) {
                // X-only at travelY.
                axisClipX(targetX, travelY, zAfterTravel);
                stage = 2;
                return;
            }

            if (stage == 2) {
                // Z-only at travelY.
                axisClipZ(targetX, travelY, targetZ);
                stage = 3;
                return;
            }

            if (stage == 3) {
                // Final vclip down/up to targetY.
                vclipToY(targetY);
                rotateToward(targetX, targetY, targetZ);

                cancel();
                active = null;
            }
        }

        /**
         * Returns a "travel Y" that prefers 300 but clamps to the world's build height if needed.
         * Uses reflection so it works across MC/Yarn signature changes.
         */
        private double getSafeTravelY() {
            double desired = TRAVEL_Y_REQUESTED;

            // Hard fallback for very old environments.
            int minY = 0;
            int maxYExclusive = 256;

            if (mc.world != null) {
                // Try World.getTopY() (no args) if present in this environment.
                Integer topYNoArgs = tryInvokeInt(mc.world, "getTopY");
                if (topYNoArgs != null) {
                    maxYExclusive = topYNoArgs;
                } else {
                    // Otherwise derive from dimension type if possible: maxYExclusive = minY + height.
                    Object dim = tryInvoke(mc.world, "getDimension");
                    if (dim != null) {
                        Integer dimMinY = firstNonNull(
                            tryInvokeInt(dim, "minY"),
                            tryInvokeInt(dim, "getMinY"),
                            tryInvokeInt(dim, "getMinimumY")
                        );

                        Integer dimHeight = firstNonNull(
                            tryInvokeInt(dim, "height"),
                            tryInvokeInt(dim, "getHeight"),
                            tryInvokeInt(dim, "logicalHeight"),
                            tryInvokeInt(dim, "getLogicalHeight")
                        );

                        if (dimMinY != null) minY = dimMinY;
                        if (dimHeight != null) maxYExclusive = minY + dimHeight;
                    } else {
                        // As another fallback, try world bottom/top style accessors if they exist.
                        Integer worldBottom = firstNonNull(
                            tryInvokeInt(mc.world, "getBottomY"),
                            tryInvokeInt(mc.world, "getMinY")
                        );
                        Integer worldHeight = firstNonNull(
                            tryInvokeInt(mc.world, "getHeight"),
                            tryInvokeInt(mc.world, "getLogicalHeight")
                        );

                        if (worldBottom != null) minY = worldBottom;
                        if (worldHeight != null) maxYExclusive = minY + worldHeight;
                    }
                }
            }

            // Keep a little headroom under the ceiling.
            double clamped = Math.min(desired, maxYExclusive - 2);
            // And don't go below minY+1.
            clamped = Math.max(clamped, minY + 1);

            return clamped;
        }

        private static Integer firstNonNull(Integer... vals) {
            for (Integer v : vals) if (v != null) return v;
            return null;
        }

        private static Object tryInvoke(Object obj, String methodName) {
            try {
                Method m = obj.getClass().getMethod(methodName);
                return m.invoke(obj);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Integer tryInvokeInt(Object obj, String methodName) {
            Object v = tryInvoke(obj, methodName);
            if (v instanceof Integer) return (Integer) v;
            return null;
        }

        private void vclipToY(double y) {
            if (mc.player == null) return;

            double x = mc.player.getX();
            double z = mc.player.getZ();

            if (mc.player.hasVehicle()) {
                Entity vehicle = mc.player.getVehicle();
                if (vehicle != null) {
                    vehicle.setPosition(vehicle.getX(), y, vehicle.getZ());
                    vehicle.fallDistance = 0;
                }
            }

            mc.player.setPosition(x, y, z);
            mc.player.fallDistance = 0;
        }

        private void axisClipX(double x, double y, double zFixed) {
            if (mc.player == null) return;

            if (mc.player.hasVehicle()) {
                Entity vehicle = mc.player.getVehicle();
                if (vehicle != null) vehicle.setPosition(x, y, zFixed);
            }

            mc.player.setPosition(x, y, zFixed);
            mc.player.fallDistance = 0;
        }

        private void axisClipZ(double xFixed, double y, double z) {
            if (mc.player == null) return;

            if (mc.player.hasVehicle()) {
                Entity vehicle = mc.player.getVehicle();
                if (vehicle != null) vehicle.setPosition(xFixed, y, z);
            }

            mc.player.setPosition(xFixed, y, z);
            mc.player.fallDistance = 0;
        }

        private void rotateToward(double x, double y, double z) {
            if (mc.player == null) return;

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
