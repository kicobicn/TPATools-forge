package com.kicobicn.TPATools.Commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.database.DatabaseManager;
import com.kicobicn.TPATools.util.ModUtils;
import com.kicobicn.TPATools.util.TeleportDelayManager;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GraveHandler {
    private static final Logger LOGGER = LogManager.getLogger("TPAtools");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String JSON_FILE = "tpatool_graves.json";

    private static final Map<UUID, BackHandler.PlayerPosition> gravePositions = new HashMap<>();

    // 加载死亡位置
    public static void loadGraves() {
        gravePositions.clear();

        Connection conn = DatabaseManager.getConnection();
        if (conn != null) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM player_graves")) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    gravePositions.put(uuid, new BackHandler.PlayerPosition(
                            ModUtils.parseIdentifier(rs.getString("dimension")),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("y_rot"), rs.getFloat("x_rot")));
                }
                ModConfigs.DebugLog.info("Loaded graves from MySQL");
                return;
            } catch (SQLException e) {
                LOGGER.error("Failed to load graves from MySQL: {}", e.getMessage());
                return;
            }
        }

        Path path = ModConfigs.getConfigDir().resolve(JSON_FILE);
        if (!Files.exists(path)) {
            return;
        }
        try {
            String jsonContent = Files.readString(path);
            Map<UUID, BackHandler.PlayerPosition> loadedGraves = GSON.fromJson(
                    jsonContent, new TypeToken<Map<UUID, BackHandler.PlayerPosition>>() {}.getType());
            if (loadedGraves != null) {
                gravePositions.putAll(loadedGraves);
                ModConfigs.DebugLog.info("Loaded graves from {}", path);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load graves: {}", e.getMessage());
        }
    }

    // 保存死亡位置
    public static void saveGraves() {
        Connection conn = DatabaseManager.getConnection();
        if (conn != null) {
            try {
                try (Statement clear = conn.createStatement()) {
                    clear.executeUpdate("DELETE FROM player_graves");
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO player_graves (player_uuid, dimension, x, y, z, x_rot, y_rot) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    for (Map.Entry<UUID, BackHandler.PlayerPosition> entry : gravePositions.entrySet()) {
                        BackHandler.PlayerPosition position = entry.getValue();
                        stmt.setString(1, entry.getKey().toString());
                        stmt.setString(2, ModUtils.formatIdentifier(position.dimension));
                        stmt.setDouble(3, position.x);
                        stmt.setDouble(4, position.y);
                        stmt.setDouble(5, position.z);
                        stmt.setFloat(6, position.xRot);
                        stmt.setFloat(7, position.yRot);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                ModConfigs.DebugLog.info("Saved graves to MySQL");
                return;
            } catch (SQLException e) {
                LOGGER.error("Failed to save graves to MySQL: {}", e.getMessage());
                return;
            }
        }

        try {
            Path path = ModConfigs.getConfigDir().resolve(JSON_FILE);
            Files.writeString(path, GSON.toJson(gravePositions));
            ModConfigs.DebugLog.info("Saved graves to {}", path);
        } catch (IOException e) {
            LOGGER.error("Failed to save graves: {}", e.getMessage());
        }
    }

    // 注册指令
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("grave")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "grave"))
                        .executes(context -> {
                            try {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                ModConfigs.DebugLog.info("Executing /grave command for player: {}", player.getName().getString());

                                if (!validateGrave(player)) {
                                    return 0;
                                }

                                // 与 /back、/home、/warp、/rtp 一致：统一走传送延迟判定
                                TeleportDelayManager.requestTeleport(player, () -> teleportToGrave(player));
                                return 1;
                            } catch (Exception e) {
                                LOGGER.error("Unexpected error in /grave command", e);
                                context.getSource().sendFailure(Component.literal("An unexpected error occurred."));
                                return 0;
                            }
                        })
        );
    }

    // 玩家死亡时记录位置
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            gravePositions.put(player.getUUID(), new BackHandler.PlayerPosition(
                    player.serverLevel(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot()
            ));
            saveGraves();
            ModConfigs.DebugLog.info("Recorded grave for {} at dimension={}, x={}, y={}, z={}",
                    player.getName().getString(), player.serverLevel().dimension().location(),
                    player.getX(), player.getY(), player.getZ());
        }
    }

    private static boolean validateGrave(ServerPlayer player) {
        BackHandler.PlayerPosition pos = gravePositions.get(player.getUUID());
        if (pos == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.grave.no_position", "No previous death position recorded!"
            ));
            return false;
        }
        ServerLevel level = player.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, pos.dimension));
        if (level == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.grave.invalid_dimension", "Invalid dimension for death position!"
            ));
            gravePositions.remove(player.getUUID());
            saveGraves();
            return false;
        }
        return true;
    }

    // 传送到死亡点
    private static int teleportToGrave(ServerPlayer player) {
        BackHandler.PlayerPosition pos = gravePositions.get(player.getUUID());
        if (pos == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.grave.no_position", "No previous death position recorded!"
            ));
            return 0;
        }
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, pos.dimension);
        ServerLevel level = player.getServer().getLevel(dimensionKey);
        if (level == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.grave.invalid_dimension", "Invalid dimension for death position!"
            ));
            gravePositions.remove(player.getUUID());
            saveGraves();
            return 0;
        }
        BackHandler.recordPosition(player);
        double gx = pos.x, gy = pos.y, gz = pos.z;
        if (ModConfigs.SAFE_TELEPORT.get()) {
            BlockPos safePos = ModUtils.findSafeTeleportPosition(level, gx, gy, gz);
            gx = safePos.getX() + 0.5;
            gy = safePos.getY();
            gz = safePos.getZ() + 0.5;
        }
        ModUtils.teleportWithAllChains(player, level, gx, gy, gz, pos.yRot, pos.xRot);
        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.grave.success", "Teleported to last death position."
        ));
        ModConfigs.DebugLog.info("Player {} teleported to grave at dimension={}, x={}, y={}, z={}",
                player.getName().getString(), pos.dimension, pos.x, pos.y, pos.z);
        return 1;
    }
}
