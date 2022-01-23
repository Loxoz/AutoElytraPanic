package fr.loxoz.autoelytrapanic;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Style;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class AutoElytraPanic implements ClientModInitializer {
    public static KeyBinding keyToggle;
    public static KeyBinding keyCancel;

    public static Config CONFIG;

    public static boolean isActive = false;

    static final String MODKEY = "autoelytrapanic";
    private static final String KEY_PREFIX = "key." + MODKEY + ".";
    private static final String KEY_CATEG = "category." + MODKEY + ".main";

    static Style chatStyle = Style.EMPTY.withColor(Formatting.GRAY).withItalic(true);

    private static KeyBinding registerKey(String name, int def) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(KEY_PREFIX + name, InputUtil.Type.KEYSYM, def, KEY_CATEG));
    }

    @Override
    public void onInitializeClient() {
        keyToggle = registerKey("toggle", GLFW.GLFW_KEY_KP_6);
        keyCancel = registerKey("cancel", GLFW.GLFW_KEY_UNKNOWN);

        AutoConfig.register(Config.class, GsonConfigSerializer::new);
        CONFIG = AutoConfig.getConfigHolder(Config.class).getConfig();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyToggle.wasPressed()) {
                CONFIG.isEnabled ^= true;
                AutoConfig.getConfigHolder(Config.class).save();
                client.inGameHud.getChatHud().addMessage(new TranslatableText("message." + MODKEY + "." + (CONFIG.isEnabled ? "enabled" : "disabled")).fillStyle(chatStyle));
            }
            while (keyCancel.wasPressed()) {
                if (isActive) {
                    isActive = false;
                    client.inGameHud.getChatHud().addMessage(new TranslatableText("message." + MODKEY + ".cancelled").fillStyle(chatStyle));
                }
            }

            if (CONFIG.isEnabled) doCheck(client);
        });
    }

    public static void doCheck(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || player.isCreative() || player.isSpectator()) return;
        if (!isActive && player.fallDistance >= CONFIG.panicFallDistance && !player.isFallFlying()) {
            ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
            if (itemStack.isOf(Items.ELYTRA) && ElytraItem.isUsable(itemStack)) {
                isActive = true;
                client.options.keyJump.setPressed(true);
                if (CONFIG.chatMessageOnActivate) {
                    client.inGameHud.getChatHud().addMessage(new TranslatableText("message." + MODKEY + ".onActivate").fillStyle(chatStyle));
                }
                player.setPitch(CONFIG.activationPitch);
                new Thread(() -> {
                    try {
                        Thread.sleep(100);
                        client.options.keyJump.setPressed(false);
                    } catch (Exception ignored) {}
                }).start();
            }
        }
        else if (isActive) {
            if (!CONFIG.isEnabled || !player.isFallFlying() || player.isOnGround() || player.isTouchingWater()) {
                isActive = false;
            }
            else {
                player.setYaw(player.getYaw() + CONFIG.additiveYaw);
            }
        }
    }
}
