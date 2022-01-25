package fr.loxoz.autoelytrapanic;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = AutoElytraPanic.MODKEY)
public class ModConfig implements ConfigData {
    @ConfigEntry.Gui.Tooltip()
    boolean isEnabled = false;
    @ConfigEntry.Gui.Tooltip()
    double panicFallDistance = 5;
    @ConfigEntry.Gui.Tooltip()
    float flyingPitch = -10;
    @ConfigEntry.Gui.Tooltip()
    float additiveYaw = 45;
    @ConfigEntry.Gui.Tooltip()
    boolean restoreView = true;
    @ConfigEntry.Gui.Tooltip()
    boolean chatMessageOnActivate = false;
}
