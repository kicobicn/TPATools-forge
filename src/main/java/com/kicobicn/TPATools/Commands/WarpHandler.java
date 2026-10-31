package com.kicobicn.TPATools.Commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kicobicn.TPATools.chat.ModChatMenus;
import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.database.DatabaseManager;
import com.kicobicn.TPATools.util.ModUtils;
import com.kicobicn.TPATools.util.TeleportDelayManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.*;

public class WarpHandler {
    
    public static final Map<String, WarpPoint> warpPoints = new HashMap<>();
    private static final Logger LOGGER = LogManager.getLogger("TPAtools");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String JSON_FILE = "tpatool_warps.json";
    
    public static class WarpPoint {
        public final String name;
        public final ResourceLocation dimension;
        public final double x, y, z;
        public final float yRot, xRot;
        
        public WarpPoint(String name, ResourceLocation dimension, double x, double y, double z, float yRot, float xRot) {
            this.name = name;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
        }
    }
    
    // 加载 warp 点
    // 加载 warp 点
    public static void loadWarpPoints() {
        warpPoints.clear();

        Connection conn = DatabaseManager.getConnection();
        if (conn != null) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM warp_points");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    ResourceLocation dimension = ModUtils.parseIdentifier(rs.getString("dimension"));
                    warpPoints.put(name, new WarpPoint(name, dimension,
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("y_rot"), rs.getFloat("x_rot")));
                }
                ModConfigs.DebugLog.info("Loaded warp points from MySQL");
                return;
            } catch (SQLException e) {
                LOGGER.error("Failed to load warp points from MySQL: {}", e.getMessage());
                return;
            }
        }

        Path path = ModConfigs.getConfigDir().resolve(JSON_FILE);
        if (!Files.exists(path)) {
            return;
        }
        try {
            String jsonContent = Files.readString(path);
            Map<String, WarpPoint> loadedWarps = GSON.fromJson(
                    jsonContent, new TypeToken<Map<String, WarpPoint>>() {}.getType());
            if (loadedWarps != null) {
                warpPoints.putAll(loadedWarps);
                ModConfigs.DebugLog.info("Loaded warp points from {}", path);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load warp points: {}", e.getMessage());
        }
    }

    // 保存 warp 点
    public static void saveWarpPoints() {
        Connection conn = DatabaseManager.getConnection();
        if (conn != null) {
            try {
                try (PreparedStatement clear = conn.prepareStatement("DELETE FROM warp_points")) {
                    clear.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO warp_points (name, dimension, x, y, z, x_rot, y_rot) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    for (Map.Entry<String, WarpPoint> entry : warpPoints.entrySet()) {
                        WarpPoint warp = entry.getValue();
                        stmt.setString(1, warp.name);
                        stmt.setString(2, ModUtils.formatIdentifier(warp.dimension));
                        stmt.setDouble(3, warp.x);
                        stmt.setDouble(4, warp.y);
                        stmt.setDouble(5, warp.z);
                        stmt.setFloat(6, warp.xRot);
                        stmt.setFloat(7, warp.yRot);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
                ModConfigs.DebugLog.info("Saved warp points to MySQL");
                return;
            } catch (SQLException e) {
                LOGGER.error("Failed to save warp points to MySQL: {}", e.getMessage());
                return;
            }
        }

        try {
            Path path = ModConfigs.getConfigDir().resolve(JSON_FILE);
            Files.writeString(path, GSON.toJson(warpPoints));
            ModConfigs.DebugLog.info("Saved warp points to {}", path);
        } catch (IOException e) {
            LOGGER.error("Failed to save warp points: {}", e.getMessage());
        }
    }
    
    // 保存 warp 点
    
    // Tab补全：warp 点名称
    private static final SuggestionProvider<CommandSourceStack> WARP_SUGGESTIONS = (context, builder) -> {
        for (String warpName : warpPoints.keySet()) {
            builder.suggest(warpName);
        }
        return builder.buildFuture();
    };

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        event.getDispatcher().register(
                Commands.literal("warp")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "warp"))
                        .executes(context ->{
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ModChatMenus.showWarpMenu(player);
                            return 1;
                        })
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> {
                                            try {
                                                ServerPlayer player = context.getSource().getPlayerOrException();
                                                String name = StringArgumentType.getString(context, "name");
                                                return setWarp(player, name);
                                            } catch (CommandSyntaxException e) {
                                                ModConfigs.DebugLog.error("Failed to parse /warp set: {}", e.getMessage());
                                                context.getSource().sendFailure(Component.literal("Invalid warp name."));
                                                return 0;
                                            } catch (Exception e) {
                                                ModConfigs.DebugLog.error("Unexpected error in /warp set: ", e);
                                                context.getSource().sendFailure(Component.literal("An unexpected error occurred."));
                                                return 0;
                                            }
                                        })))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(WARP_SUGGESTIONS)
                                        .executes(context -> {
                                            try {
                                                ServerPlayer player = context.getSource().getPlayerOrException();
                                                String name = StringArgumentType.getString(context, "name");
                                                if (!warpPoints.containsKey(name)) {
                                                    player.sendSystemMessage(ModUtils.translateWithFallback(
                                                            "command.tpatools.warp.not_found", "Warp point '%s' not found!", name));
                                                    return 0;
                                                }
                                                TeleportDelayManager.requestTeleport(player, () -> teleportToWarp(player, name));
                                                return 1;
                                            } catch (CommandSyntaxException e) {
                                                ModConfigs.DebugLog.error("Failed to parse /warp tp: {}", e.getMessage());
                                                context.getSource().sendFailure(Component.literal("Invalid warp name."));
                                                return 0;
                                            } catch (Exception e) {
                                                ModConfigs.DebugLog.error("Unexpected error in /warp tp: ", e);
                                                context.getSource().sendFailure(Component.literal("An unexpected error occurred."));
                                                return 0;
                                            }
                                        })))
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ModChatMenus.showWarpList(player, 0);
                                    return 1;
                                }))
                        .then(Commands.literal("remove")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(WARP_SUGGESTIONS)
                                        .executes(context -> {
                                            try {
                                                ServerPlayer player = context.getSource().getPlayerOrException();
                                                String name = StringArgumentType.getString(context, "name");
                                                return removeWarp(player, name);
                                            } catch (CommandSyntaxException e) {
                                                ModConfigs.DebugLog.error("Failed to parse /warp remove: {}", e.getMessage());
                                                context.getSource().sendFailure(Component.literal("Invalid warp name."));
                                                return 0;
                                            } catch (Exception e) {
                                                ModConfigs.DebugLog.error("Unexpected error in /warp remove: ", e);
                                                context.getSource().sendFailure(Component.literal("An unexpected error occurred."));
                                                return 0;
                                            }
                                        })))
        );
    }
    
    private static int setWarp(ServerPlayer player, String name) {
        if (warpPoints.size() >= ModConfigs.MAX_WARP_COUNT.get()) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.warp.limit_exceeded", "Warp limit exceeded! Maximum allowed: %s", ModConfigs.MAX_WARP_COUNT.get()
            ));
            return 0;
        }
        
        if (warpPoints.containsKey(name)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.warp.name_exists", "Warp point '%s' already exists!", name
            ));
            return 0;
        }
        
        WarpPoint warp = new WarpPoint(name, player.serverLevel().dimension().location(),
                player.getX(), player.getY(), player.getZ(), 
                player.getYRot(), player.getXRot());
        warpPoints.put(name, warp);
        saveWarpPoints();
        
        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.warp.set", "Warp point '%s' set at your current position.", name
        ));
        ModConfigs.DebugLog.info("Player {} set warp point {} at dimension={}, x={}, y={}, z={}",
                player.getName().getString(), name, player.serverLevel().dimension().location(),
                player.getX(), player.getY(), player.getZ());
        return 1;
    }
    
    private static int teleportToWarp(ServerPlayer player, String name) {
        WarpPoint warp = warpPoints.get(name);
        if (warp == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.warp.not_found", "Warp point '%s' not found!", name
            ));
            return 0;
        }
        
        ServerLevel level = player.getServer().getLevel(ResourceKey.create(
                Registries.DIMENSION, warp.dimension));
        if (level == null) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.warp.invalid_dimension", "Invalid dimension for warp point '%s'!", name
            ));
            return 0;
        }
        
        BackHandler.recordPosition(player);
        ModUtils.teleportWithAllChains(player, level, warp.x, warp.y, warp.z, warp.yRot, warp.xRot);
        
        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.warp.teleported", "Teleported to warp point '%s'.", name
        ));
        ModConfigs.DebugLog.info("Player {} teleported to warp point {} at dimension={}, x={}, y={}, z={}",
                player.getName().getString(), name, warp.dimension,
                warp.x, warp.y, warp.z);
        return 1;
    }
    
    private static int removeWarp(ServerPlayer player, String name) {
        if (!warpPoints.containsKey(name)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.warp.not_found", "Warp point '%s' not found!", name
            ));
            return 0;
        }
        
        warpPoints.remove(name);
        saveWarpPoints();
        
        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.warp.removed", "Warp point '%s' removed.", name
        ));
        ModConfigs.DebugLog.info("Player {} removed warp point {}", player.getName().getString(), name);
        return 1;
    }
}