package com.kicobicn.TPATools.util;

import com.kicobicn.TPATools.config.ModConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportDelayManager {
    private static final Logger LOGGER = LogManager.getLogger("TPAtools");

    private static final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();
    private static final double MOVEMENT_THRESHOLD = 0.1;

    public static class PendingTeleport {
        public final ServerPlayer player;
        public final Runnable teleportAction;
        public final long startTime;
        public final int delaySeconds;
        public double startX, startY, startZ;
        public float startYaw, startPitch;
        public float lastHealth;
        public long lastActionBarUpdate = 0;
        public int animationFrame = 0;
        public long lastSoundTime = 0;
        public int lastSecondPlayed = -1;

        public PendingTeleport(ServerPlayer player, Runnable teleportAction, int delaySeconds) {
            this.player = player;
            this.teleportAction = teleportAction;
            this.startTime = System.currentTimeMillis();
            this.delaySeconds = delaySeconds;
            this.startX = player.getX();
            this.startY = player.getY();
            this.startZ = player.getZ();
            this.startYaw = player.getYRot();
            this.startPitch = player.getXRot();
            this.lastHealth = player.getHealth();
        }

        public int getRemainingSeconds() {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            return Math.max(0, delaySeconds - (int) elapsed);
        }

        public int getRemainingTicks() {
            long elapsed = System.currentTimeMillis() - startTime;
            int totalTicks = delaySeconds * 20;
            int elapsedTicks = (int) (elapsed / 50);
            return Math.max(0, totalTicks - elapsedTicks);
        }

        public boolean hasMoved() {
            double dx = player.getX() - startX;
            double dy = player.getY() - startY;
            double dz = player.getZ() - startZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            return distance > MOVEMENT_THRESHOLD;
        }

        public boolean isHurt() {
            return player.getHealth() < lastHealth;
        }

        public void updateHealth() {
            lastHealth = player.getHealth();
        }

        public boolean isComplete() {
            return getRemainingSeconds() <= 0;
        }
    }

    public static void requestTeleport(ServerPlayer player, Runnable teleportAction) {
        if (!ModConfigs.TELEPORT_NEED_TIME.get() || player.isCreative()) {
            teleportAction.run();
            return;
        }

        UUID uuid = player.getUUID();

        if (pendingTeleports.containsKey(uuid)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.teleport_delay.already_pending",
                    "You already have a pending teleport. Please wait or move to cancel."
            ));
            return;
        }

        int delay = ModConfigs.TELEPORT_DELAY_TIME.get();
        PendingTeleport pending = new PendingTeleport(player, teleportAction, delay);
        pendingTeleports.put(uuid, pending);

        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.teleport_delay.started",
                "Teleporting in %d seconds. Don't move!",
                delay
        ));

        updateActionBarHUD(pending);
        ModConfigs.DebugLog.info("Player {} started teleport delay ({}s)",
                player.getName().getString(), delay);
    }

    /** 外部主动清除某个玩家的待处理传送（例如玩家退出服务器） */
    public static void clear(UUID uuid) {
        if (pendingTeleports.remove(uuid) != null) {
            ModConfigs.DebugLog.info("Cleared pending teleport for {}", uuid);
        }
    }

    public static boolean hasPendingTeleport(ServerPlayer player) {
        return pendingTeleports.containsKey(player.getUUID());
    }

    public static PendingTeleport getPendingTeleport(ServerPlayer player) {
        return pendingTeleports.get(player.getUUID());
    }

    public static void tick() {
        if (!ModConfigs.TELEPORT_NEED_TIME.get()) {
            return;
        }

        List<UUID> completed = new ArrayList<>();
        List<UUID> cancelled = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<UUID, PendingTeleport> entry : pendingTeleports.entrySet()) {
            PendingTeleport pending = entry.getValue();
            ServerPlayer player = pending.player;

            if (!player.isAlive() || player.hasDisconnected() || player.connection == null) {
                player.sendSystemMessage(Component.literal(""), true);
                cancelled.add(entry.getKey());
                continue;
            }

            if (pending.hasMoved()) {
                player.sendSystemMessage(Component.literal(""), true);
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.teleport_delay.moved",
                        "You moved! Teleport cancelled."
                ).withStyle(ChatFormatting.RED));
                cancelled.add(entry.getKey());
                continue;
            }

            if (pending.isHurt()) {
                player.sendSystemMessage(Component.literal(""), true);
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.teleport_delay.hurt",
                        "You took damage! Teleport cancelled."
                ).withStyle(ChatFormatting.RED));
                cancelled.add(entry.getKey());
                continue;
            }

            pending.updateHealth();

            if (pending.isComplete()) {
                try {
                    pending.teleportAction.run();
                    playTeleportSound(player);
                    showTeleportSuccess(player);
                    completed.add(entry.getKey());
                    ModConfigs.DebugLog.info("Player {} completed teleport delay",
                            player.getName().getString());
                } catch (Exception e) {
                    LOGGER.error("Error executing delayed teleport for {}: {}",
                            player.getName().getString(), e.getMessage());
                    cancelled.add(entry.getKey());
                }
            } else {
                if (currentTime - pending.lastActionBarUpdate >= 50) {
                    pending.animationFrame++;
                    updateActionBarHUD(pending);
                    pending.lastActionBarUpdate = currentTime;
                }

                playCountdownSound(pending, currentTime);
            }
        }

        completed.forEach(pendingTeleports::remove);
        cancelled.forEach(pendingTeleports::remove);
    }

    private static void updateActionBarHUD(PendingTeleport pending) {
        int remaining = pending.getRemainingSeconds();
        if (remaining <= 0) {
            return;
        }

        ChatFormatting numberColor = getNumberColor(remaining);
        String progressBar = generateProgressBar(pending);

        Component hudMessage = Component.literal("")
                .append(Component.literal("[" + remaining + "]").withStyle(numberColor, ChatFormatting.BOLD))
                .append(Component.literal(" "))
                .append(Component.literal(progressBar).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(ModUtils.translateWithFallback(
                        "command.tpatools.teleport_delay.instruction",
                        "About to be transmitted. Please do not move",
                        remaining
                )).withStyle(ChatFormatting.RED);

        pending.player.sendSystemMessage(hudMessage, true);
    }

    private static ChatFormatting getNumberColor(int remaining) {
        if (remaining <= 2) {
            return ChatFormatting.RED;
        }
        if (remaining <= 3) {
            return ChatFormatting.GOLD;
        }
        return ChatFormatting.YELLOW;
    }

    private static String generateProgressBar(PendingTeleport pending) {
        int totalTicks = pending.delaySeconds * 20;
        int remainingTicks = pending.getRemainingTicks();
        int completedTicks = totalTicks - remainingTicks;

        int totalBars = 10;
        int filledBars = (int) ((double) completedTicks / totalTicks * totalBars);
        int emptyBars = totalBars - filledBars;

        StringBuilder bar = new StringBuilder("[");
        bar.append("█".repeat(Math.max(0, filledBars)));
        bar.append("░".repeat(Math.max(0, emptyBars)));
        bar.append("]");

        return bar.toString();
    }

    private static void playCountdownSound(PendingTeleport pending, long currentTime) {
        int currentSecond = pending.getRemainingSeconds();

        if (currentSecond <= 0 || currentSecond >= pending.delaySeconds) {
            return;
        }

        if (currentSecond != pending.lastSecondPlayed && currentTime - pending.lastSoundTime >= 900) {
            ServerPlayer player = pending.player;
            float pitch = getSoundPitch(currentSecond);
            float volume = getSoundVolume(currentSecond);

            player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, volume, pitch);

            pending.lastSecondPlayed = currentSecond;
            pending.lastSoundTime = currentTime;
            ModConfigs.DebugLog.info("Playing countdown sound for player {}, second: {}, pitch: {}",
                    player.getName().getString(), currentSecond, pitch);
        }
    }

    private static float getSoundPitch(int remainingSeconds) {
        return switch (remainingSeconds) {
            case 1 -> 2.0f;
            case 2 -> 1.7f;
            case 3 -> 1.4f;
            default -> 1.0f;
        };
    }

    private static float getSoundVolume(int remainingSeconds) {
        return switch (remainingSeconds) {
            case 1 -> 1.0f;
            case 2 -> 0.8f;
            case 3 -> 0.7f;
            default -> 0.5f;
        };
    }

    private static void playTeleportSound(ServerPlayer player) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 1.5f);
        ModConfigs.DebugLog.info("Playing teleport success sound for player: {}",
                player.getName().getString());
    }

    private static void showTeleportSuccess(ServerPlayer player) {
        Component successMsg = Component.literal("")
                .append(ModUtils.translateWithFallback("command.tpatools.teleport_delay.success", "Teleportation successful"))
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        player.sendSystemMessage(successMsg, true);
    }
}
