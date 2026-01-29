package com.promptt.pclip;

import com.promptt.pclip.commands.PclipCommand;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;

public class PclipAddon extends MeteorAddon {
    @Override
    public void onInitialize() {
        Commands.add(new PclipCommand());
    }

    @Override
    public String getPackage() {
        return "com.promptt.pclip";
    }
}
