package piolunson.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import piolunson.addon.SpeedMineAddon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SpeedMine extends Module {
    public enum AutoTool {
        None,
        Normal,
        Silent
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRebreak = settings.createGroup("Rebreak");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance to the block being mined.")
        .defaultValue(4.5)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Block damage required before the break packets are sent.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(1.0)
        .build()
    );

    private final Setting<Boolean> doubleBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("double-break")
        .description("Mine two blocks at the same time.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> instant = sgGeneral.add(new BoolSetting.Builder()
        .name("instant")
        .description("Sends the break packets immediately after clicking.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> multitask = sgGeneral.add(new BoolSetting.Builder()
        .name("multitask")
        .description("Keeps mining while you are using an item.")
        .defaultValue(false)
        .build()
    );

    private final Setting<AutoTool> autoTool = sgGeneral.add(new EnumSetting.Builder<AutoTool>()
        .name("auto-tool")
        .description("Swaps to the best tool before sending the break packet.")
        .defaultValue(AutoTool.Silent)
        .build()
    );

    private final Setting<Boolean> rebreak = sgRebreak.add(new BoolSetting.Builder()
        .name("rebreak")
        .description("Sends the break packets again while the block is still there.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rebreakDelay = sgRebreak.add(new IntSetting.Builder()
        .name("rebreak-delay")
        .description("Milliseconds between rebreak attempts.")
        .defaultValue(500)
        .min(0)
        .sliderMax(2000)
        .visible(rebreak::get)
        .build()
    );

    private final Setting<Integer> rebreakAttempts = sgRebreak.add(new IntSetting.Builder()
        .name("rebreak-attempts")
        .description("How many times a single block is re-broken before giving up.")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .visible(rebreak::get)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders the block being mined.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lines = sgRender.add(new ColorSetting.Builder()
        .name("lines")
        .description("Line color of the mining highlight.")
        .defaultValue(new SettingColor(140, 142, 255, 255))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sides = sgRender.add(new ColorSetting.Builder()
        .name("sides")
        .description("Side color of the mining highlight.")
        .defaultValue(new SettingColor(140, 142, 255, 50))
        .visible(render::get)
        .build()
    );

    private final List<MiningData> targets = new ArrayList<>();

    public SpeedMine() {
        super(SpeedMineAddon.CATEGORY, "speed-mine", "Packet mining with automatic rebreak.");
    }

    @Override
    public void onActivate() {
        targets.clear();
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        InvUtils.swapBack();
    }

    @Override
    public String getInfoString() {
        if (mc.player == null || mc.world == null || targets.isEmpty()) return null;
        MiningData data = targets.get(0);
        return String.format("%.0f%%", Math.min(data.damage / speed.get(), 1.0) * 100);
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) return;

        event.cancel();

        BlockPos pos = event.blockPos;
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;
        if (mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > range.get() * range.get()) return;
        if (targets.stream().anyMatch(target -> target.pos.equals(pos))) return;

        int limit = doubleBreak.get() ? 2 : 1;
        if (targets.size() >= limit) {
            if (limit == 1) targets.clear();
            else return;
        }

        targets.add(new MiningData(pos, event.direction, mineDamage(state, pos)));
        startMining(targets.get(targets.size() - 1));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        Iterator<MiningData> iterator = targets.iterator();

        while (iterator.hasNext()) {
            MiningData data = iterator.next();
            BlockState state = mc.world.getBlockState(data.pos);

            if (state.isAir()) {
                iterator.remove();
                continue;
            }

            if (mc.player.getEyePos().squaredDistanceTo(data.pos.toCenterPos()) > range.get() * range.get()) {
                iterator.remove();
                continue;
            }

            if (mc.player.isUsingItem() && !multitask.get()) continue;

            if (!data.started) {
                startMining(data);
                continue;
            }

            if (!data.sent) {
                data.damage += instant.get() ? 1.0f : mineDamage(state, data.pos);
                if (data.damage >= speed.get()) sendBreak(data);
                continue;
            }

            if (rebreak.get() && now - data.sentAt >= rebreakDelay.get()) {
                if (data.attempts >= rebreakAttempts.get()) {
                    iterator.remove();
                    continue;
                }

                data.attempts++;
                data.damage = 0.0f;
                data.sent = false;
                startMining(data);
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;

        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            handleBlockUpdate(packet);
        } else if (event.packet instanceof BundleS2CPacket bundle) {
            for (Packet<?> packet : bundle.getPackets()) {
                if (packet instanceof BlockUpdateS2CPacket blockUpdate) handleBlockUpdate(blockUpdate);
            }
        }
    }

    private void handleBlockUpdate(BlockUpdateS2CPacket packet) {
        for (MiningData data : targets) {
            if (!data.pos.equals(packet.getPos())) continue;

            if (packet.getState().isAir()) {
                data.damage = 0.0f;
                data.sent = false;
            } else if (data.sent && rebreak.get() && data.attempts < rebreakAttempts.get()) {
                data.attempts++;
                data.sent = false;
                data.damage = 0.0f;
            }
        }
    }

    private void startMining(MiningData data) {
        if (mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, data.pos, data.direction
        ));
        mc.player.swingHand(Hand.MAIN_HAND);

        data.started = true;
        data.sent = false;
        data.damage = 0.0f;
    }

    private void sendBreak(MiningData data) {
        if (mc.getNetworkHandler() == null) return;

        int slot = bestSlot(mc.world.getBlockState(data.pos));

        if (autoTool.get() != AutoTool.None && slot != -1 && slot != mc.player.getInventory().getSelectedSlot()) {
            InvUtils.swap(slot, autoTool.get() == AutoTool.Silent);
        }

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, data.pos, data.direction
        ));
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, data.pos, data.direction
        ));
        mc.player.swingHand(Hand.MAIN_HAND);

        data.sent = true;
        data.sentAt = System.currentTimeMillis();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || targets.isEmpty()) return;

        for (MiningData data : targets) {
            double progress = MathHelper.clamp(data.damage / speed.get(), 0.0, 1.0);
            double size = progress / 2.0;

            Box box = new Box(
                data.pos.getX() + 0.5 - size,
                data.pos.getY() + 0.5 - size,
                data.pos.getZ() + 0.5 - size,
                data.pos.getX() + 0.5 + size,
                data.pos.getY() + 0.5 + size,
                data.pos.getZ() + 0.5 + size
            );

            event.renderer.box(box, sides.get(), lines.get(), shapeMode.get(), 0);
        }
    }

    private float mineDamage(BlockState state, BlockPos pos) {
        float hardness = state.getHardness(mc.world, pos);
        if (hardness < 0) return 0.0f;
        if (hardness == 0) return 1.0f;

        int slot = bestSlot(state);
        ItemStack stack = slot == -1 ? ItemStack.EMPTY : mc.player.getInventory().getStack(slot);

        float speedMultiplier = stack.getMiningSpeedMultiplier(state);
        int efficiency = EnchantmentHelper.getLevel(
            mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY),
            stack
        );
        if (efficiency > 0 && speedMultiplier > 1.0f) speedMultiplier += efficiency * efficiency + 1;

        if (StatusEffectUtil.hasHaste(mc.player)) {
            speedMultiplier *= 1.0f + (StatusEffectUtil.getHasteAmplifier(mc.player) + 1) * 0.2f;
        }

        if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            float multiplier = switch (mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 8.1e-4f;
            };
            speedMultiplier *= multiplier;
        }

        if (!mc.player.isOnGround()) speedMultiplier /= 5.0f;

        int divider = stack.isSuitableFor(state) ? 30 : 100;
        return speedMultiplier / hardness / divider;
    }

    private int bestSlot(BlockState state) {
        int best = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }

        return best;
    }

    private static class MiningData {
        final BlockPos pos;
        final Direction direction;
        float damage;
        boolean started;
        boolean sent;
        long sentAt;
        int attempts;

        MiningData(BlockPos pos, Direction direction, float damage) {
            this.pos = pos;
            this.direction = direction;
            this.damage = damage;
        }
    }
}
