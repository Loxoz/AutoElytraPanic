package fr.loxoz.autoelytrapanic;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.ActionResult;

@Config(name = AutoElytraPanic.MOD_ID)
public class AEPConfig implements ConfigData {
    public boolean enabled = false;
    @ConfigEntry.Gui.Tooltip()
    public double panicFallDistance = 5;
    @ConfigEntry.Gui.Tooltip()
    public float flyingPitch = -10;
    @ConfigEntry.Gui.Tooltip()
    public float yawPerTick = 45;
    @ConfigEntry.Gui.Tooltip()
    public boolean restoreView = true;
    @ConfigEntry.Gui.Tooltip()
    public boolean ignoreIfDamaged = true;
    @ConfigEntry.Gui.Tooltip()
    public boolean messageOnActivate = true;
    @ConfigEntry.Gui.Tooltip()
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    public ChatOutputMode messageOutputMode = ChatOutputMode.ACTION_BAR;

    public static AEPConfig init() {
        ConfigHolder<AEPConfig> holder = AutoConfig.register(AEPConfig.class, GsonConfigSerializer::new);
        holder.registerSaveListener((configHolder, aepConfig) -> holder.getConfig().validate());
        return holder.getConfig();
    }

    public static void clothSave() {
        AutoConfig.getConfigHolder(AEPConfig.class).save();
    }

    public static Screen getConfigScreen(Screen parent) {
        return AutoConfig.getConfigScreen(AEPConfig.class, parent).get();
    }

    public ActionResult validate() {
        if (panicFallDistance < 0.1) panicFallDistance = 0.1;
        return ActionResult.SUCCESS;
    }

    public void save() { clothSave(); }

    public enum ChatOutputMode {
        CHAT,
        ACTION_BAR
    }
}
