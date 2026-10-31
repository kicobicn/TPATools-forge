package com.kicobicn.TPATools.Commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.database.DatabaseManager;
import com.kicobicn.TPATools.util.ModUtils;
import com.kicobicn.TPATools.util.TeleportDelayManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RTPHandler {
    private static final Logger LOGGER = LogManager.getLogger("TPAtools");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String JSON_FILE = "tpatool_rtp_cooldowns.json";

    /** 粗扫步长：先每 4 格采样一次表面，再在命中区间内逐格细查 */
    private static final int COARSE_STEP = 4;

    // 冷却时间管理
    private static final Map<UUID, Long> rtpCooldowns = new HashMap<>();
    private static final Random random = new Random();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("rtp")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "rtp"))
                        .executes(context -> {
                            try {
                                ServerPlayer player = context.getSource().getPlayerOrException();

                                if (!validateRTPCooldown(player)) {
                                    return 0;
                                }

                                // 与 /back、/grave、/home、/warp 一致：统一走传送延迟判定
                                TeleportDelayManager.requestTeleport(player, () -> executeRTP(player));
                                return 1;
                            } catch (Exception e) {
                                LOGGER.error("Unexpected error in /rtp", e);
                                context.getSource().sendFailure(Component.literal("An unexpected error occurred."));
                                return 0;
                            }
                        })
        );
    }

    // ==================== 冷却持久化 ====================
    public static void loadCooldowns() {
        rtpCooldowns.clear();

        Connection conn = DatabaseManager.getConnection();
        if (conn != null) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM rtp_cooldowns");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rtpCooldowns.put(UUID.fromString(rs.getString("player_uuid")), rs.getLong("last_used"));
                }
                ModConfigs.DebugLog.info("Loaded RTP cooldowns from MySQL");
                return;
            } catch (SQLException e) {
                LOGGER.error("Failed to load RTP cooldowns from MySQL: {}", e.getMessage());
                return;
            }
        }

        Path path = ModConfigs.getConfigDir().resolve(JSON_FILE);
        if (!Files.exists(path)) {
            return;
        }
        try {
            String json = Files.readString(path);
            Map<UUID, Long> loaded = GSON.fromJson(json, new TypeToken<Map<UUID, Long>>() {}.getType());
            if (loaded != null) {
                rtpCooldowns.putAll(loaded);
                ModConfigs.DebugLog.info("Loaded RTP cooldowns from {}", path);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load RTP cooldowns: {}", e.getMessage());
        }
    }

    public static void saveCooldowns() {
        Connection conn = DatabaseManager.getConnection();
        if (conn != null) {
            try {
                try (PreparedStatement clear = conn.prepareStatement("DELETE FROM rtp_cooldowns")) {
                    clear.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                        "REPLACE INTO rtp_cooldowns (player_uuid, last_used) VALUES (?, ?)")) {
                    for (Map.Entry<UUID, Long> entry : rtpCooldowns.entrySet()) {
                        stmt.setString(1, entry.getKey().toString());
                        stmt.setLong(2, entry.getValue());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                return;
            } catch (SQLException e) {
                LOGGER.error("Failed to save RTP cooldowns to MySQL: {}", e.getMessage());
                return;
            }
        }

        try {
            Files.writeString(ModConfigs.getConfigDir().resolve(JSON_FILE), GSON.toJson(rtpCooldowns));
        } catch (IOException e) {
            LOGGER.error("Failed to save RTP cooldowns: {}", e.getMessage());
        }
    }

    // ==================== 执行 RTP ====================
    private static int executeRTP(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        if (isOnCooldown(playerUUID)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.rtp.cooldown",
                    "RTP is on cooldown. Please wait %d seconds.",
                    getRemainingCooldown(playerUUID)));
            return 0;
        }

        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.rtp.searching", "Searching for a safe location..."));

        ServerLevel world = player.serverLevel();
        // 以世界原点 (0,0) 为中心进行随机传送
        BlockPos safePos = findSafeLocation(world, 0, 0);

        if (safePos == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.rtp.failed",
                    "Failed to find a safe location after %d attempts. Please try again.",
                    ModConfigs.RTP_MAX_ATTEMPTS.get()));
            return 0;
        }

        BackHandler.recordPosition(player);

        // 统一走骑乘链传送；findSafeLocation 已经保证该点可站立，无需再做一次安全落点搜索
        ModUtils.teleportWithAllChains(player, world,
                safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5,
                player.getYRot(), player.getXRot());

        setCooldown(playerUUID);

        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.rtp.success",
                "Successfully teleported to X: %d, Y: %d, Z: %d",
                safePos.getX(), safePos.getY(), safePos.getZ()).withStyle(ChatFormatting.GREEN));

        ModConfigs.DebugLog.info("Player {} used RTP to X: {}, Y: {}, Z: {}",
                player.getName().getString(), safePos.getX(), safePos.getY(), safePos.getZ());
        return 1;
    }

    // ==================== 位置搜索 ====================
    private static BlockPos findSafeLocation(ServerLevel world, int centerX, int centerZ) {
        int scope = Math.max(1, ModConfigs.RTP_SCOPE.get());
        int maxAttempts = Math.max(1, ModConfigs.RTP_MAX_ATTEMPTS.get());
        int minY = ModConfigs.RTP_MIN_Y.get();
        int maxY = ModConfigs.RTP_MAX_Y.get();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = centerX + random.nextInt(scope * 2 + 1) - scope;
            int z = centerZ + random.nextInt(scope * 2 + 1) - scope;

            BlockPos found = scanColumn(world, x, z, maxY, minY);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 在单个区块列上寻找最高可站立点。
     * <p>先在 Y 轴上按 {@link #COARSE_STEP} 格粗扫，只有当某段出现“上空气、下实心”的
     * 落层时才对该段逐格细查；这样把主线程的 {@code getBlockState} 调用次数降到原来的 1/4 左右，
     * 显著降低随机传送导致的卡顿。</p>
     */
    private static BlockPos scanColumn(ServerLevel world, int x, int z, int maxY, int minY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, maxY, z);

        for (int y = maxY; y >= minY; y -= COARSE_STEP) {
            pos.setY(y);
            if (world.getBlockState(pos).isAir()) {
                continue;
            }
            pos.setY(Math.min(maxY, y + COARSE_STEP));
            if (!world.getBlockState(pos).isAir()) {
                // 这一整段都埋在地下，直接跳过
                continue;
            }

            int top = Math.min(maxY, y + COARSE_STEP);
            int bottom = Math.max(minY, y - 1);
            for (int refineY = top; refineY >= bottom; refineY--) {
                pos.setY(refineY);
                if (isSafePosition(world, pos)) {
                    return pos.immutable();
                }
            }
        }
        return null;
    }

    /**
     * 检查该位置是否可安全站立
     */
    private static boolean isSafePosition(ServerLevel world, BlockPos pos) {
        if (pos.getY() < ModConfigs.RTP_MIN_Y.get() || pos.getY() >= ModConfigs.RTP_MAX_Y.get()) {
            return false;
        }

        BlockState blockState = world.getBlockState(pos);
        BlockState blockBelow = world.getBlockState(pos.below());
        BlockState blockAbove = world.getBlockState(pos.above());

        // 脚下必须是能站立的方块
        if (!blockBelow.blocksMotion()
                || blockBelow.is(Blocks.LAVA) || blockBelow.is(Blocks.MAGMA_BLOCK)
                || blockBelow.is(Blocks.FIRE) || blockBelow.is(Blocks.SOUL_FIRE)
                || blockBelow.is(Blocks.CACTUS) || blockBelow.is(Blocks.WATER)
                || blockBelow.is(Blocks.SWEET_BERRY_BUSH)) {
            return false;
        }

        // 脚部与头部必须可通过，且不是液体 / 危险方块
        if (blockState.blocksMotion()
                || blockState.is(Blocks.LAVA) || blockState.is(Blocks.MAGMA_BLOCK)
                || blockState.is(Blocks.FIRE) || blockState.is(Blocks.SOUL_FIRE)
                || blockState.is(Blocks.CACTUS) || blockState.is(Blocks.SWEET_BERRY_BUSH)) {
            return false;
        }

        FluidState fluidState = blockState.getFluidState();
        if (!fluidState.isEmpty()) {
            return false;
        }

        // 头顶两格必须是空气，避免卡进树/岩层缝隙
        if (!blockAbove.isAir() || !world.getBlockState(pos.above(2)).isAir()) {
            return false;
        }

        return true;
    }

    // ==================== 冷却 ====================
    private static boolean isOnCooldown(UUID playerUUID) {
        Long lastUsed = rtpCooldowns.get(playerUUID);
        if (lastUsed == null) {
            return false;
        }
        long cooldownTime = ModConfigs.RTP_COOLDOWN_TIME.get() * 1000L;
        return System.currentTimeMillis() - lastUsed < cooldownTime;
    }

    private static long getRemainingCooldown(UUID playerUUID) {
        Long lastUsed = rtpCooldowns.get(playerUUID);
        if (lastUsed == null) {
            return 0;
        }
        long cooldownTime = ModConfigs.RTP_COOLDOWN_TIME.get() * 1000L;
        long remaining = cooldownTime - (System.currentTimeMillis() - lastUsed);
        return Math.max(0, (remaining + 999) / 1000); // 向上取整到秒
    }

    private static void setCooldown(UUID playerUUID) {
        rtpCooldowns.put(playerUUID, System.currentTimeMillis());
        saveCooldowns();
    }

    /** 清除冷却时间（用于调试或重置） */
    public static void clearCooldown(UUID playerUUID) {
        rtpCooldowns.remove(playerUUID);
        saveCooldowns();
    }

    /** 获取所有冷却中的玩家 */
    public static Map<UUID, Long> getCooldowns() {
        return new HashMap<>(rtpCooldowns);
    }

    private static boolean validateRTPCooldown(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        if (isOnCooldown(playerUUID)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.rtp.cooldown",
                    "RTP is on cooldown. Please wait %d seconds.",
                    getRemainingCooldown(playerUUID)));
            return false;
        }
        return true;
    }
}
