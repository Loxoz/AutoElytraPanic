package fr.loxoz.autoelytrapanic;

import me.shedaniel.autoconfig.ConfigData;

@me.shedaniel.autoconfig.annotation.Config(name = AutoElytraPanic.MODKEY)
public class Config implements ConfigData {
    boolean isEnabled = false;
    int panicFallDistance = 10;
    float activationPitch = -20;
    float additiveYaw = 45;
    boolean chatMessageOnActivate = false;
}
