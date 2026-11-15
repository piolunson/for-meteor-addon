package piolunson.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.Vec3d;
import piolunson.addon.SpeedMineAddon;

/**
 * A session journal: it does not touch packets, it just remembers what happened while it was on and
 * prints a short report when you turn it off. Everything is client-side.
 */
public class Journal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> trackDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("track-distance")
        .description("Counts how far you have walked.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trackMined = sgGeneral.add(new BoolSetting.Builder()
        .name("track-mined")
        .description("Counts the blocks you finished breaking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trackDeaths = sgGeneral.add(new BoolSetting.Builder()
        .name("track-deaths")
        .description("Counts your deaths.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> printOnDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("print-on-disable")
        .description("Prints the session summary in chat when the module is turned off.")
        .defaultValue(true)
        .build()
    );

    private Vec3d lastPos;
    private float lastHealth;
    private long startedAt;
    private int mined;
    private int deaths;
    private double distance;

    public Journal() {
        super(SpeedMineAddon.CATEGORY, "journal", "Keeps a client-side log of the current session and reports it when disabled.");
    }

    @Override
    public void onActivate() {
        lastPos = mc.player != null ? new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()) : null;
        lastHealth = mc.player != null ? mc.player.getHealth() : 20.0f;
        startedAt = System.currentTimeMillis();
        mined = 0;
        deaths = 0;
        distance = 0.0;
    }

    @Override
    public void onDeactivate() {
        if (!printOnDisable.get()) return;

        info("Session journal: %s played, %d blocks mined, %.0f m walked, %d deaths.",
            formatTime(System.currentTimeMillis() - startedAt),
            trackMined.get() ? mined : 0,
            trackDistance.get() ? distance : 0.0,
            trackDeaths.get() ? deaths : 0
        );
    }

    @Override
    public String getInfoString() {
        return trackMined.get() ? String.valueOf(mined) : null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (trackDistance.get()) {
            Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

            if (lastPos != null) {
                double step = pos.distanceTo(lastPos);
                // A teleport or a respawn is not walking.
                if (step > 0.0 && step < 16.0) distance += step;
            }

            lastPos = pos;
        }

        if (trackDeaths.get()) {
            float health = mc.player.getHealth();
            if (lastHealth > 0.0f && health <= 0.0f) deaths++;
            lastHealth = health;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!trackMined.get()) return;

        if (event.packet instanceof PlayerActionC2SPacket packet
            && packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
            mined++;
        }
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
