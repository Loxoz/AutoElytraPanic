package fr.loxoz.autoelytrapanic;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.function.Function;

@Environment(EnvType.CLIENT)
public class AutoElytraPanic implements ClientModInitializer {
    public static final String MOD_ID = "autoelytrapanic";
    public static KeyBinding keyToggle;
    public static KeyBinding keyCancel;

    public static ModConfig CONFIG;

    public static boolean isActive = false;
    public static boolean hasSentDurabilityMsg = false;

    static Style chatStyle = Style.EMPTY.withColor(Formatting.GRAY).withItalic(true);

    public static int EASE_STEPS = 5;
    static Float prevYaw = null;
    static Float prevPitch = null;

    private static KeyBinding registerKey(String name, int def) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key." + MOD_ID + "." + name, InputUtil.Type.KEYSYM, def, "category." + MOD_ID + ".main"));
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
                client.inGameHud.getChatHud().addMessage(new TranslatableText("message." + MOD_ID + "." + (CONFIG.isEnabled ? "enabled" : "disabled")).fillStyle(chatStyle));
            }
            while (keyCancel.wasPressed()) {
                if (isActive) {
                    isActive = false;
                    client.inGameHud.getChatHud().addMessage(new TranslatableText("message." + MOD_ID + ".cancelled").fillStyle(chatStyle));
                }
            }

            if (CONFIG.isEnabled) doCheck(client);
        });
    }

    @SuppressWarnings("UnnecessaryUnboxing")
    public static boolean isBlockIgnored(BlockState state) {
        Block[] blocks = { Blocks.WATER, Blocks.SLIME_BLOCK, Blocks.SEAGRASS, Blocks.KELP, Blocks.KELP_PLANT };
        for (Block b : blocks) if (state.isOf(b)) return true;
        Block block = state.getBlock();
        if (block instanceof SlabBlock && state.get(SlabBlock.WATERLOGGED).booleanValue() && state.get(SlabBlock.TYPE) == SlabType.BOTTOM) return true;
        // maybe later, check collision shape with waterlogged blocks
        return false;
    }

    public static void doCheck(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || player.isCreative() || player.isSpectator()) return;
        double panicFallDist = Math.max(CONFIG.panicFallDistance, 0.1); // to avoid just always activating when you set a value near zero or less
        if (!isActive && !hasSentDurabilityMsg && player.fallDistance >= panicFallDist && !player.isFallFlying()) {
            ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
            if (!itemStack.isOf(Items.ELYTRA) || !ElytraItem.isUsable(itemStack)) return;

            if (!CONFIG.ignoreIfDamaged && itemStack.getMaxDamage() - itemStack.getDamage() < 5) {
                hasSentDurabilityMsg = true;
                client.inGameHud.getChatHud().addMessage(new TranslatableText("message." + MOD_ID + ".elytraDamaged").fillStyle(chatStyle));
                return;
            }

            BlockHit groundBlock = downwardBlockIterator(player.world, player.getBlockPos(),
                    hit -> !isBlockIgnored(hit.state) && hit.state.getCollisionShape(player.world, hit.pos).isEmpty() // ignore no collision
            );
            if (groundBlock == null || isBlockIgnored(groundBlock.state)) return;

            double groundDist = player.getPos().distanceTo(groundBlock.getPos3d());

            double minGroundDist = Math.max(Math.sqrt(player.fallDistance * 10), 10); // the best function I found to compute the min ground distance from fall distance to not take damage

            if (groundDist < minGroundDist) {
                setActive(true);
                client.options.keyJump.setPressed(true);
                if (CONFIG.chatMessageOnActivate) {
                    client.inGameHud.getChatHud().addMessage(new TranslatableText("message." + MOD_ID + ".onActivate").fillStyle(chatStyle));
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
            if (!CONFIG.isEnabled || canDisable(player)) setActive(false);
            else {
                player.setPitch(CONFIG.flyingPitch);
                float yaw = CONFIG.additiveYaw / EASE_STEPS;
                new Thread(() -> { // maybe this could be done in a better way
                    try {
                        for (int i = 0; i < EASE_STEPS; i++) {
                            if (i != 0) Thread.sleep(50 / EASE_STEPS);
                            player.setYaw(player.getYaw() + yaw);
                        }
                    } catch (Exception ignored) {}
                }).start();
            }
        }
        else if (hasSentDurabilityMsg && canDisable(player)) {
            hasSentDurabilityMsg = false;
        }
    }

    public static boolean canDisable(ClientPlayerEntity player) {
        return !player.isFallFlying() || player.isOnGround() || player.isTouchingWater();
    }

    public static void setActive(boolean active) {
        if (isActive == active) return;
        isActive = active;
        hasSentDurabilityMsg = false;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (CONFIG.restoreView && player != null) {
            if (active) {
                prevYaw = player.getYaw();
                prevPitch = player.getPitch();
            }
            else if (prevYaw != null && prevPitch != null) {
                player.setYaw(prevYaw);
                player.setPitch(prevPitch);
            }
        }
    }

    public static BlockHit downwardBlockIterator(World world, BlockPos startPos, Function<BlockHit, Boolean> skip) {
        BlockPos pos = startPos;
        while (skip.apply(new BlockHit(pos, world.getBlockState(pos))) && pos.getY() >= world.getBottomY()) {
            pos = pos.add(0,-1,0);
        }
        if (pos.getY() < world.getBottomY()) return null;
        return new BlockHit(pos, world.getBlockState(pos));
    }

    public static class BlockHit {
        BlockPos pos;
        BlockState state;
        public BlockHit(BlockPos pos, BlockState state) {
            this.pos = pos;
            this.state = state;
        }

        public Vec3d getPos3d() {
            return new Vec3d(pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
