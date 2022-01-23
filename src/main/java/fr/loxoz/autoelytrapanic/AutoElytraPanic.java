package fr.loxoz.autoelytrapanic;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiFunction;
import java.util.function.Function;

@Environment(EnvType.CLIENT)
public class AutoElytraPanic implements ClientModInitializer {
    public static KeyBinding keyToggle;
    public static KeyBinding keyCancel;

    public static ModConfig CONFIG;

    public static boolean isActive = false;

    static final String MODKEY = "autoelytrapanic";
    private static final String KEY_PREFIX = "key." + MODKEY + ".";
    private static final String KEY_CATEG = "category." + MODKEY + ".main";

    static Style chatStyle = Style.EMPTY.withColor(Formatting.GRAY).withItalic(true);

    static int easeSteps = 5;

    private static KeyBinding registerKey(String name, int def) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(KEY_PREFIX + name, InputUtil.Type.KEYSYM, def, KEY_CATEG));
    }

    @Override
    public void onInitializeClient() {
        keyToggle = registerKey("toggle", GLFW.GLFW_KEY_KP_6);
        keyCancel = registerKey("cancel", GLFW.GLFW_KEY_UNKNOWN);

        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        CONFIG = AutoConfig.getConfigHolder(ModConfig.class).getConfig();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyToggle.wasPressed()) {
                CONFIG.isEnabled ^= true;
                AutoConfig.getConfigHolder(ModConfig.class).save();
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
        double fallDist = CONFIG.panicFallDistance <= 0 ? 0.01 : CONFIG.panicFallDistance; // to avoid just always activating when you set a stupid value
        if (!isActive && player.fallDistance >= fallDist && !player.isFallFlying()) {
            BlockState groundBlock = downwardBlockIterator(player.world, player.getBlockPos(),
                    (state, pos) -> !state.isOf(Blocks.WATER) && state.getCollisionShape(player.world, pos).isEmpty() // will stop at any solid block or water
            );
            if (groundBlock == null || groundBlock.isOf(Blocks.WATER)) return;

            ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
            if (itemStack.isOf(Items.ELYTRA) && ElytraItem.isUsable(itemStack)) {
                isActive = true;
                client.options.keyJump.setPressed(true);
                if (CONFIG.chatMessageOnActivate) {
                    client.inGameHud.getChatHud().addMessage(new TranslatableText("message." + MODKEY + ".onActivate").fillStyle(chatStyle));
                }
                player.setPitch(CONFIG.flyingPitch);
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
                player.setPitch(CONFIG.flyingPitch);
                float yaw = CONFIG.additiveYaw / easeSteps;
                new Thread(() -> {
                    try {
                        for (int i = 0; i < easeSteps; i++) {
                            if (i != 0) Thread.sleep(50 / easeSteps);
                            player.setYaw(player.getYaw() + yaw);
                        }
                    } catch (Exception ignored) {}
                }).start();
            }
        }
    }

    public static BlockState downwardBlockIterator(World world, BlockPos startPos, BiFunction<BlockState, BlockPos, Boolean> skip) {
        BlockPos pos = startPos;
        while (skip.apply(world.getBlockState(pos), pos) && pos.getY() >= world.getBottomY()) {
            pos = pos.add(0,-1,0);
        }
        if (pos.getY() < world.getBottomY()) return null;
        return world.getBlockState(pos);
    }
}
