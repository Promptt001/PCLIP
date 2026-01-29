package com.promptt.pclip.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

public class PclipCommand extends Command {
    public PclipCommand() {
        super("pclip", "Rotates to face an X/Z coordinate, then clips horizontally to it. Usage: .pclip <x> <z>");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("x", DoubleArgumentType.doubleArg())
            .then(argument("z", DoubleArgumentType.doubleArg())
                .executes(context -> {
                    if (mc.player == null) return SINGLE_SUCCESS;

                    double targetX = context.getArgument("x", Double.class);
                    double targetZ = context.getArgument("z", Double.class);

                    double px = mc.player.getX();
                    double pz = mc.player.getZ();
                    double dx = targetX - px;
                    double dz = targetZ - pz;

                    // Rotate yaw to face the target X/Z (pitch is left unchanged since Y isn't provided).
                    float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                    mc.player.setYaw(MathHelper.wrapDegrees(yaw));

                    // "HClip" horizontally to the exact X/Z, keeping current Y.
                    double y = mc.player.getY();

                    // Mirror Meteor's hclip behavior: move vehicle too if mounted.
                    if (mc.player.hasVehicle()) {
                        Entity vehicle = mc.player.getVehicle();
                        if (vehicle != null) vehicle.setPosition(targetX, vehicle.getY(), targetZ);
                    }

                    mc.player.setPosition(targetX, y, targetZ);

                    double blocks = Math.sqrt(dx * dx + dz * dz);
                    ChatUtils.info("PClipped %.2f blocks to X=%.2f Z=%.2f.", blocks, targetX, targetZ);

                    return SINGLE_SUCCESS;
                })
            )
        );
    }
}
