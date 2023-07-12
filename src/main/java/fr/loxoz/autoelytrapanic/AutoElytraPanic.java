package fr.loxoz.autoelytrapanic;

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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

@Environment(EnvType.CLIENT)
public class AutoElytraPanic implements ClientModInitializer {
    public static final String MOD_ID = "autoelytrapanic";
    private static final Style chatStyle = Style.EMPTY.withColor(Formatting.GRAY).withItalic(true);
    private static AutoElytraPanic instance = null;
    // public static int EASE_STEPS = 5;

    public KeyBinding keyToggle = null;
    public KeyBinding keyCancel = null;
    private AEPConfig config = null;
    private boolean active = false;
    private boolean msgDurabilitySent = false;
    private Float restoreYaw = null;
    private Float restorePitch = null;
    private Float prevYaw = null;
    private int jumpTicks = -1;

    private static KeyBinding registerKey(String name, int def) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key." + MOD_ID + "." + name, InputUtil.Type.KEYSYM, def, "category." + MOD_ID + ".main"));
    }

    @Contract(pure = true)
    @Nullable
    public static AutoElytraPanic inst() { return instance; }

    public AutoElytraPanic() {
        instance = this;
    }

    @Override
    public void onInitializeClient() {
        keyToggle = registerKey("toggle", InputUtil.GLFW_KEY_KP_6);
        keyCancel = registerKey("cancel", InputUtil.UNKNOWN_KEY.getCode());

        config = AEPConfig.init();

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    public AEPConfig getConfig() { return config; }
    public void setEnabled(boolean enabled) {
        if (config.enabled == enabled) return;
        config.enabled = enabled;
        config.save();
    }
    /** short for {@link AEPConfig#enabled} */
    public boolean isEnabled() { return config.enabled; }
    public boolean isActive() { return active; }

    private void tick(MinecraftClient client) {
        while (keyToggle.wasPressed()) {
            setEnabled(!isEnabled());
            postMessage(Text.translatable("message." + MOD_ID + "." + (isEnabled() ? "enabled" : "disabled")).fillStyle(chatStyle));
        }
        while (keyCancel.wasPressed()) {
            if (active) {
                active = false;
                postMessage(Text.translatable("message." + MOD_ID + ".cancelled").fillStyle(chatStyle));
            }
        }

        if (isEnabled()) tickChecker(client);
        if (jumpTicks >= 0) {
            jumpTicks --;
            client.options.jumpKey.setPressed(jumpTicks > 0 && client.player != null);
        }
    }

    private void tickChecker(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || player.isCreative() || player.isSpectator()) return;
        if (!active && !msgDurabilitySent && player.fallDistance >= config.panicFallDistance && !player.isFallFlying()) {
            ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
            if (!itemStack.isOf(Items.ELYTRA) || !ElytraItem.isUsable(itemStack)) return;

            if (!config.ignoreIfDamaged && itemStack.getMaxDamage() - itemStack.getDamage() < 5) {
                msgDurabilitySent = true;
                postMessage(Text.translatable("message." + MOD_ID + ".elytraDamaged").fillStyle(chatStyle));
                return;
            }

            BlockHit groundBlock = downwardBlockIterator(player.getWorld(), player.getBlockPos(),
                    hit -> !isBlockIgnored(hit.state) && hit.state.getCollisionShape(player.getWorld(), hit.pos).isEmpty() // ignore no collision
            );
            if (groundBlock == null || isBlockIgnored(groundBlock.state)) return;

            double groundDist = player.getPos().distanceTo(groundBlock.getPos3d());

            double minGroundDist = Math.max(Math.sqrt(player.fallDistance * 10), 10); // the best function I found to compute the min ground distance from fall distance to not take damage

            if (groundDist < minGroundDist) {
                setActive(true);
                jumpTicks = 2;
                if (config.messageOnActivate) {
                    postMessage(Text.translatable("message." + MOD_ID + ".onActivate").fillStyle(chatStyle));
                }
                player.setPitch(config.flyingPitch);
            }
        }
        else if (active) {
            if (!isEnabled() || canDisable(player)) setActive(false);
            else {
                player.setPitch(config.flyingPitch);
                if (prevYaw != null) {
                    player.setYaw(prevYaw + config.yawPerTick);
                }
                prevYaw = player.getYaw();
            }
        }
        else if (msgDurabilitySent && canDisable(player)) {
            msgDurabilitySent = false;
        }
    }

    public void onRenderTick(MinecraftClient client, float tickDelta) {
        if (active && prevYaw != null && client.player != null) {
            client.player.setYaw(prevYaw + (config.yawPerTick * tickDelta));
        }
    }

    private void postMessage(Text msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        switch (config.messageOutputMode) {
            case CHAT -> client.inGameHud.getChatHud().addMessage(msg);
            case ACTION_BAR -> client.inGameHud.setOverlayMessage(msg, false);
        }
    }

    /**
     * This method is meant to be used by the mod itself,
     * warning not to be confused with the enabled property in the config,
     * see {@link AutoElytraPanic#setEnabled(boolean)}
     */
    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        msgDurabilitySent = false;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        if (active) {
            restoreYaw = player.getYaw();
            restorePitch = player.getPitch();
        }
        else {
            prevYaw = null;
            if (config.restoreView) {
                player.setYaw(restoreYaw);
                player.setPitch(restorePitch);
            }
            restoreYaw = null;
            restorePitch = null;
        }
    }

    // helpers
    public static boolean isBlockIgnored(BlockState state) {
        Block[] blocks = { Blocks.WATER, Blocks.SLIME_BLOCK, Blocks.SEAGRASS, Blocks.KELP, Blocks.KELP_PLANT };
        for (Block b : blocks) if (state.isOf(b)) return true;
        Block block = state.getBlock();
        if (block instanceof SlabBlock && state.get(SlabBlock.WATERLOGGED) && state.get(SlabBlock.TYPE) == SlabType.BOTTOM) return true;
        // maybe later, check collision shape with waterlogged blocks
        return false;
    }

    public static boolean canDisable(ClientPlayerEntity player) {
        return !player.isFallFlying() || player.isOnGround() || player.isTouchingWater();
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
