package com.kicobicn.TPATools.Commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kicobicn.TPATools.chat.ModChatMenus;
import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.database.DatabaseManager;
import com.kicobicn.TPATools.util.ModUtils;
import com.kicobicn.TPATools.util.TeleportDelayManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HomeHandler {
    public static final Map<UUID, Map<String, Home>> playerHomes = new ConcurrentHashMap<>();
    public static final Map<String, Map<String, PublicHomeInfo>> publicHomesByOwner = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LogManager.getLogger("TPAtools");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    //PublicHomeInfo 类，用于存储公开家的 UUID 和用户名
    public static class PublicHomeInfo {
        public UUID ownerUUID;
        public String ownerName;
        public String homeName;
        public PublicHomeInfo(UUID ownerUUID, String ownerName, String homeName) {
            this.ownerUUID = ownerUUID;
            this.ownerName = ownerName;
            this.homeName = homeName;
        }
    }

    // 存储最后位置的映射
    private static final Map<UUID, Home.Position> lastPositions = new HashMap<>();

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static class Home {
        public static class Position {
            public double x, y, z;
            public float xRot, yRot;
            public ResourceLocation dimension; // 确认类型为 ResourceLocation
            public Position(double x, double y, double z, float xRot, float yRot, ResourceLocation dimension) {
                this.x = x; this.y = y; this.z = z;
                this.xRot = xRot; this.yRot = yRot; this.dimension = dimension;
            }
        }
        public Position position;
        public List<UUID> sharedPlayers;
        public Home(Position position, List<UUID> sharedPlayers) {
            this.position = position;
            this.sharedPlayers = sharedPlayers != null ? sharedPlayers : new ArrayList<>();
        }
    }

    private static void recordLastPosition(ServerPlayer player) {
        ResourceLocation dimension = player.level().dimension().location();
        lastPositions.put(player.getUUID(), new Home.Position(
                player.getX(), player.getY(), player.getZ(),
                player.getXRot(), player.getYRot(), dimension
        ));
    }
//邀请系统
    private static final Map<String, Long> inviteCooldowns = new ConcurrentHashMap<>();
    private static final Map<UUID, List<InviteRequest>> pendingInvites = new ConcurrentHashMap<>();

    public static class InviteRequest {
        public final UUID senderUUID;
        public final String senderName;
        public final String homeName;
        public final long timestamp;

        public InviteRequest(UUID senderUUID, String senderName, String homeName) {
            this.senderUUID = senderUUID;
            this.senderName = senderName;
            this.homeName = homeName;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // ==================== 持久化 ====================
    public static void loadHomes() {
        playerHomes.clear();
        publicHomesByOwner.clear();

        Connection conn = DatabaseManager.getConnection();
        if (conn != null) {
            try {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM player_homes")) {
                    while (rs.next()) {
                        UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                        String homeName = rs.getString("home_name");
                        ResourceLocation dimension = ModUtils.parseIdentifier(rs.getString("dimension"));
                        Home.Position pos = new Home.Position(
                                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                                rs.getFloat("x_rot"), rs.getFloat("y_rot"), dimension);
                        playerHomes.computeIfAbsent(playerUUID, k -> new HashMap<>())
                                .put(homeName, new Home(pos, new ArrayList<>()));
                    }
                }

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM home_shares")) {
                    while (rs.next()) {
                        UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                        String homeName = rs.getString("home_name");
                        UUID sharedWith = UUID.fromString(rs.getString("shared_with"));
                        Map<String, Home> homes = playerHomes.get(playerUUID);
                        Home home = homes == null ? null : homes.get(homeName);
                        if (home != null && !home.sharedPlayers.contains(sharedWith)) {
                            home.sharedPlayers.add(sharedWith);
                        }
                    }
                }

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM public_homes")) {
                    while (rs.next()) {
                        UUID ownerUUID = UUID.fromString(rs.getString("player_uuid"));
                        String ownerName = rs.getString("player_name");
                        String homeName = rs.getString("home_name");
                        publicHomesByOwner.computeIfAbsent(ownerUUID.toString(), k -> new HashMap<>())
                                .put(homeName, new PublicHomeInfo(ownerUUID, ownerName, homeName));
                    }
                }
                ModConfigs.DebugLog.info("Loaded homes and public homes from MySQL");
                return;
            } catch (SQLException | RuntimeException e) {
                LOGGER.error("Failed to load homes from MySQL: {}", e.getMessage());
                return;
            }
        }

        Path path = ModConfigs.getConfigDir().resolve("tpatool_homes.json");
        if (!Files.exists(path)) {
            return;
        }
        try {
            String jsonContent = Files.readString(path);
            Map<String, Object> data = GSON.fromJson(jsonContent, new TypeToken<Map<String, Object>>() {}.getType());
            if (data == null) {
                return;
            }
            Map<UUID, Map<String, Home>> loadedHomes = GSON.fromJson(
                    GSON.toJson(data.get("playerHomes")), new TypeToken<Map<UUID, Map<String, Home>>>() {}.getType());
            if (loadedHomes != null) {
                playerHomes.putAll(loadedHomes);
            }
            Map<String, Map<String, PublicHomeInfo>> loadedPublicHomes = GSON.fromJson(
                    GSON.toJson(data.get("publicHomesByOwner")),
                    new TypeToken<Map<String, Map<String, PublicHomeInfo>>>() {}.getType());
            if (loadedPublicHomes != null) {
                publicHomesByOwner.putAll(loadedPublicHomes);
            }
            ModConfigs.DebugLog.info("Loaded homes and public homes from {}", path);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load homes: {}", e.getMessage());
        }
    }

    public static void saveHomes() {
        Connection conn = DatabaseManager.getConnection();
        if (conn != null) {
            try {
                // 先删子表再删主表，避免外键约束报错
                try (Statement clear = conn.createStatement()) {
                    clear.executeUpdate("DELETE FROM home_shares");
                    clear.executeUpdate("DELETE FROM public_homes");
                    clear.executeUpdate("DELETE FROM player_homes");
                }

                try (PreparedStatement homeStmt = conn.prepareStatement(
                             "INSERT INTO player_homes (player_uuid, home_name, dimension, x, y, z, x_rot, y_rot) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
                     PreparedStatement shareStmt = conn.prepareStatement(
                             "INSERT INTO home_shares (player_uuid, home_name, shared_with) VALUES (?, ?, ?)");
                     PreparedStatement publicStmt = conn.prepareStatement(
                             "INSERT INTO public_homes (player_uuid, player_name, home_name) VALUES (?, ?, ?)")) {

                    for (Map.Entry<UUID, Map<String, Home>> playerEntry : playerHomes.entrySet()) {
                        UUID playerUUID = playerEntry.getKey();
                        for (Map.Entry<String, Home> homeEntry : playerEntry.getValue().entrySet()) {
                            String homeName = homeEntry.getKey();
                            Home home = homeEntry.getValue();

                            homeStmt.setString(1, playerUUID.toString());
                            homeStmt.setString(2, homeName);
                            homeStmt.setString(3, ModUtils.formatIdentifier(home.position.dimension));
                            homeStmt.setDouble(4, home.position.x);
                            homeStmt.setDouble(5, home.position.y);
                            homeStmt.setDouble(6, home.position.z);
                            homeStmt.setFloat(7, home.position.xRot);
                            homeStmt.setFloat(8, home.position.yRot);
                            homeStmt.addBatch();

                            for (UUID sharedWith : home.sharedPlayers) {
                                shareStmt.setString(1, playerUUID.toString());
                                shareStmt.setString(2, homeName);
                                shareStmt.setString(3, sharedWith.toString());
                                shareStmt.addBatch();
                            }
                        }
                    }

                    for (Map.Entry<String, Map<String, PublicHomeInfo>> ownerEntry : publicHomesByOwner.entrySet()) {
                        for (Map.Entry<String, PublicHomeInfo> homeEntry : ownerEntry.getValue().entrySet()) {
                            PublicHomeInfo info = homeEntry.getValue();
                            publicStmt.setString(1, info.ownerUUID.toString());
                            publicStmt.setString(2, info.ownerName == null ? "Unknown" : info.ownerName);
                            publicStmt.setString(3, homeEntry.getKey());
                            publicStmt.addBatch();
                        }
                    }

                    homeStmt.executeBatch();
                    shareStmt.executeBatch();
                    publicStmt.executeBatch();
                }
                ModConfigs.DebugLog.info("Saved homes and public homes to MySQL");
                return;
            } catch (SQLException e) {
                LOGGER.error("Failed to save homes to MySQL: {}", e.getMessage());
                return;
            }
        }

        try {
            Path path = ModConfigs.getConfigDir().resolve("tpatool_homes.json");
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            Map<String, Object> data = new HashMap<>();
            data.put("playerHomes", playerHomes);

            Map<String, Map<String, PublicHomeInfo>> publicHomesData = new HashMap<>();
            for (Map.Entry<String, Map<String, PublicHomeInfo>> ownerEntry : publicHomesByOwner.entrySet()) {
                String ownerUUID = ownerEntry.getKey();
                Map<String, PublicHomeInfo> ownerPublicHomes = new HashMap<>();
                for (Map.Entry<String, PublicHomeInfo> homeEntry : ownerEntry.getValue().entrySet()) {
                    String homeName = homeEntry.getKey();
                    PublicHomeInfo info = homeEntry.getValue();
                    String ownerName = info.ownerName != null ? info.ownerName
                            : (server != null && server.getProfileCache() != null
                                    ? server.getProfileCache().get(UUID.fromString(ownerUUID))
                                            .map(GameProfile::getName).orElse("Unknown")
                                    : "Unknown");
                    ownerPublicHomes.put(homeName, new PublicHomeInfo(UUID.fromString(ownerUUID), ownerName, homeName));
                }
                publicHomesData.put(ownerUUID, ownerPublicHomes);
            }
            data.put("publicHomesByOwner", publicHomesData);

            Files.writeString(path, GSON.toJson(data));
            ModConfigs.DebugLog.info("Saved homes and public homes to {}", path);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to save homes: {}", e.getMessage());
        }
    }

    // Tab补全：玩家自己的家
    private static final SuggestionProvider<CommandSourceStack> OWN_HOME_SUGGESTIONS = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes != null) {
            for (String homeName : homes.keySet()) {
                builder.suggest(homeName);
            }
        }
        return builder.buildFuture();
    };
    // Tab补全：公开的家
    private static final SuggestionProvider<CommandSourceStack> PUBLIC_HOME_SUGGESTIONS = (context, builder) -> {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(player.getUUID().toString());

            if (ownerPublicHomes != null) {
                for (String homeName : ownerPublicHomes.keySet()) {
                    builder.suggest(homeName);
                }
            }
            return builder.buildFuture();
        } catch (CommandSyntaxException e) {
            return builder.buildFuture();
        }
    };

    // Tab补全：非公开的家
    private static final SuggestionProvider<CommandSourceStack> NON_PUBLIC_HOME_SUGGESTIONS = (context, builder) -> {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Map<String, Home> homes = playerHomes.get(player.getUUID());
            Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(player.getUUID().toString());

            if (homes != null) {
                for (String homeName : homes.keySet()) {
                    // 检查家园是否不是公开的
                    if (ownerPublicHomes == null || !ownerPublicHomes.containsKey(homeName)) {
                        builder.suggest(homeName);
                    }
                }
            }
            return builder.buildFuture();
        } catch (CommandSyntaxException e) {
            return builder.buildFuture();
        }
    };

    // Tab补全：可访问的家（自己的）
    private static final SuggestionProvider<CommandSourceStack> ACCESSIBLE_HOME_SUGGESTIONS = (context, builder) -> {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Map<String, Home> homes = playerHomes.get(player.getUUID());
            if (homes != null) {
                for (String homeName : homes.keySet()) {
                    builder.suggest(homeName);
                }
            }
            return builder.buildFuture();
        } catch (CommandSyntaxException e) {
            ModConfigs.DebugLog.error("Failed to provide suggestions for /home tp: {}", e.getMessage());
            return builder.buildFuture();
        }
    };


    // Tab补全：/home otherhome 的 playername:homename
    private static final SuggestionProvider<CommandSourceStack> OTHER_HOME_SUGGESTIONS = (context, builder) -> {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            // 处理公开家园
            for (Map.Entry<String, Map<String, PublicHomeInfo>> ownerEntry : publicHomesByOwner.entrySet()) {
                String ownerUUIDStr = ownerEntry.getKey();
                Map<String, PublicHomeInfo> ownerHomes = ownerEntry.getValue();

                UUID ownerUUID = UUID.fromString(ownerUUIDStr);
                String ownerName = player.getServer().getProfileCache().get(ownerUUID)
                        .map(GameProfile::getName).orElse("Unknown");

                for (String homeName : ownerHomes.keySet()) {
                    builder.suggest(ownerName + ":" + homeName);
                }
            }

            // 处理私有分享的家园
            for (Map.Entry<UUID, Map<String, Home>> entry : playerHomes.entrySet()) {
                UUID ownerUUID = entry.getKey();
                if (!ownerUUID.equals(player.getUUID())) {
                    String ownerName = player.getServer().getProfileCache().get(ownerUUID)
                            .map(GameProfile::getName).orElse("Unknown");
                    for (Map.Entry<String, Home> homeEntry : entry.getValue().entrySet()) {
                        if (homeEntry.getValue().sharedPlayers.contains(player.getUUID())) {
                            builder.suggest(ownerName + ":" + homeEntry.getKey());
                        }
                    }
                }
            }
            return builder.buildFuture();
        } catch (CommandSyntaxException e) {
            ModConfigs.DebugLog.error("Failed to provide suggestions for /home otherhome: {}", e.getMessage());
            return builder.buildFuture();
        }
    };


    // Tab补全：/home sharelist 的 in/out 参数
    private static final SuggestionProvider<CommandSourceStack> SHARELIST_SUGGESTIONS = (context, builder) -> {
        builder.suggest("in");
        builder.suggest("out");
        return builder.buildFuture();
    };

    // Tab 补全：/home unshare 的玩家名（支持离线玩家）
    private static final SuggestionProvider<CommandSourceStack> PLAYER_NAME_SUGGESTIONS = (context, builder) -> {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            MinecraftServer server = player.getServer();
            if (server != null) {
                // 从 playerHomes 获取所有 UUID，转换为玩家名
                for (UUID uuid : playerHomes.keySet()) {
                    server.getProfileCache().get(uuid)
                            .map(GameProfile::getName)
                            .ifPresent(builder::suggest);
                }
            }
            return builder.buildFuture();
        } catch (CommandSyntaxException e) {
            ModConfigs.DebugLog.error("Failed to provide player name suggestions: {}", e.getMessage());
            return builder.buildFuture();
        }
    };


    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        event.getDispatcher().register(
                Commands.literal("home")
                        .requires(source -> ModConfigs.checkCommandPermission(source, "home"))
                        .executes(context ->{
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ModChatMenus.HomeMenus.showHomeMenu(player);
                            return 1;
                        })
                        .then(Commands.literal("tp")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(ACCESSIBLE_HOME_SUGGESTIONS)
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String homeName = StringArgumentType.getString(context, "name");
                                            if (!validateHomeAccessible(player, homeName)) {
                                                return 0;
                                            }
                                            TeleportDelayManager.requestTeleport(player, () -> teleportToHome(player, homeName));
                                            return 1;
                                        })))
                        .then(Commands.literal("set")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(context -> setHome(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ModChatMenus.HomeMenus.showOwnHomesList(player, 0);  // 显示玩家自己的家列表，第0页
                                    return 1;
                                })
                                .then(Commands.literal("page")
                                        .then(Commands.argument("page", IntegerArgumentType.integer(0))
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    int page = IntegerArgumentType.getInteger(context, "page");
                                                    ModChatMenus.HomeMenus.showOwnHomesList(player, page);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(OWN_HOME_SUGGESTIONS)
                                        .executes(context -> removeHome(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("rename")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(OWN_HOME_SUGGESTIONS)
                                        .then(Commands.argument("newName", StringArgumentType.string())
                                                .executes(context -> renameHome(
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "name"),
                                                        StringArgumentType.getString(context, "newName")
                                                )))))
                        .then(Commands.literal("share")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(OWN_HOME_SUGGESTIONS)
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> shareHome(
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "name"),
                                                        EntityArgument.getPlayer(context, "player")
                                                )))))
                        .then(Commands.literal("public")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(NON_PUBLIC_HOME_SUGGESTIONS)
                                        .executes(context -> setPublicHome(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("private")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(PUBLIC_HOME_SUGGESTIONS)
                                        .executes(context -> setPrivateHome(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name")))))
                        .then(Commands.literal("otherhome")
                                .then(Commands.argument("home", StringArgumentType.greedyString())
                                        .suggests(OTHER_HOME_SUGGESTIONS)
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String homeArg = StringArgumentType.getString(context, "home");
                                            if (!validateOtherHomeExists(player, homeArg)) {
                                                return 0;
                                            }
                                            TeleportDelayManager.requestTeleport(player, () -> teleportToOtherHome(player, homeArg));
                                            return 1;
                                        })))
                        .then(Commands.literal("otherlist")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ModChatMenus.HomeMenus.showPublicHomesList(player, 0);
                                    return 1;
                                })
                                .then(Commands.literal("page")
                                        .then(Commands.argument("page", IntegerArgumentType.integer(0))
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    int page = IntegerArgumentType.getInteger(context, "page");
                                                    ModChatMenus.HomeMenus.showPublicHomesList(player, page);
                                                    return 1;
                                                }))))
                        .then(Commands.literal("sharelist")
                                .then(Commands.argument("type", StringArgumentType.string())
                                        .suggests(SHARELIST_SUGGESTIONS)
                                        .executes(context -> {  // /home sharelist <type> 指令
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String type = StringArgumentType.getString(context, "type");
                                            if ("out".equals(type)) {
                                                ModChatMenus.HomeMenus.showSharedOutList(player, 0);  // 显示分享出去的家，第0页
                                            } else if ("in".equals(type)) {
                                                ModChatMenus.HomeMenus.showSharedInList(player, 0);  // 显示分享给自己的家，第0页
                                            }
                                            return 1;
                                        })
                                        .then(Commands.literal("page")
                                                .then(Commands.argument("page", IntegerArgumentType.integer(0))
                                                        .executes(context -> {
                                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                                            String type = StringArgumentType.getString(context, "type");
                                                            int page = IntegerArgumentType.getInteger(context, "page");
                                                            if ("out".equals(type)) {
                                                                ModChatMenus.HomeMenus.showSharedOutList(player, page);
                                                            } else if ("in".equals(type)) {
                                                                ModChatMenus.HomeMenus.showSharedInList(player, page);
                                                            }
                                                            return 1;
                                                        })))))
                        .then(Commands.literal("unshare")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(OWN_HOME_SUGGESTIONS)
                                        .executes(context -> unshareHome(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "name"),
                                                null
                                        ))
                                        .then(Commands.argument("player", StringArgumentType.string())
                                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                                .executes(context -> unshareHome(
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "name"),
                                                        StringArgumentType.getString(context, "player")
                                                )))))
                        .then(Commands.literal("invite")
                                .then(Commands.literal("invite")
                                        .then(Commands.argument("homename", StringArgumentType.string())
                                                .suggests(OWN_HOME_SUGGESTIONS)
                                                .then(Commands.argument("playername", EntityArgument.player())
                                                        .executes(context -> {
                                                            ServerPlayer sender = context.getSource().getPlayerOrException();
                                                            ServerPlayer target = EntityArgument.getPlayer(context, "playername");
                                                            String homeName = StringArgumentType.getString(context, "homename");
                                                            return sendInvite(context.getSource(), sender, target, homeName);
                                                        }))))
                                .then(Commands.literal("cancel")
                                        .then(Commands.argument("playername", EntityArgument.player())
                                                .executes(context -> {
                                                    ServerPlayer sender = context.getSource().getPlayerOrException();
                                                    ServerPlayer target = EntityArgument.getPlayer(context, "playername");
                                                    return cancelInvite(context.getSource(), sender, target);
                                                }))
                                        .executes(context -> {
                                            ServerPlayer sender = context.getSource().getPlayerOrException();
                                            return cancelAllInvites(context.getSource(), sender);
                                        }))
                                .then(Commands.literal("accept")
                                        .then(Commands.argument("playername", EntityArgument.player())
                                                .executes(context -> {
                                                    ServerPlayer receiver = context.getSource().getPlayerOrException();
                                                    ServerPlayer sender = EntityArgument.getPlayer(context, "playername");
                                                    return acceptInvite(context.getSource(), receiver, sender);
                                                }))
                                        .executes(context -> {
                                            ServerPlayer receiver = context.getSource().getPlayerOrException();
                                            return acceptLatestInvite(context.getSource(), receiver);
                                        }))
                                .then(Commands.literal("deny")
                                        .then(Commands.argument("playername", EntityArgument.player())
                                                .executes(context -> {
                                                    ServerPlayer receiver = context.getSource().getPlayerOrException();
                                                    ServerPlayer sender = EntityArgument.getPlayer(context, "playername");
                                                    return denyInvite(context.getSource(), receiver, sender);
                                                }))
                                        .executes(context -> {
                                            ServerPlayer receiver = context.getSource().getPlayerOrException();
                                            return denyLatestInvite(context.getSource(), receiver);
                                        })))


        );
    }

    private static int setHome(ServerPlayer player, String homeName) {
        Map<String, Home> homes = playerHomes.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        if (homes.size() >= ModConfigs.MAX_HOMES.get()) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.limit_exceeded", "You have reached the maximum number of homes (%s)!", ModConfigs.MAX_HOMES.get()
            ));
            return 0;
        }
        if (homes.containsKey(homeName)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.name_exists", "A home named %s already exists!", homeName
            ));
            return 0;
        }
        ResourceLocation dimension = player.level().dimension().location();
        Home.Position position = new Home.Position(
                player.getX(), player.getY(), player.getZ(),
                player.getXRot(), player.getYRot(), dimension
        );
        homes.put(homeName, new Home(position, new ArrayList<>()));
        saveHomes();
        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.home.set", "Home %s set at your current position.", homeName
        ));
        ModConfigs.DebugLog.info("Player {} set home {} at {}", player.getName().getString(), homeName, dimension);
        return 1;
    }


    private static int teleportToHome(ServerPlayer player, String name) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes != null && homes.containsKey(name)) {
            Home home = homes.get(name);
            ServerLevel level = player.getServer().getLevel(ResourceKey.create(
                    Registries.DIMENSION, home.position.dimension));
            if (level == null) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.invalid_dimension", "Invalid dimension for home %s!", name
                ));
                return 0;
            }
            BackHandler.recordPosition(player);
            double homeX = home.position.x, homeY = home.position.y, homeZ = home.position.z;
            if (ModConfigs.SAFE_TELEPORT.get()) {
                BlockPos safePos = ModUtils.findSafeTeleportPosition(level, homeX, homeY, homeZ);
                homeX = safePos.getX() + 0.5;
                homeY = safePos.getY();
                homeZ = safePos.getZ() + 0.5;
            }
            ModUtils.teleportWithAllChains(player, level, homeX, homeY, homeZ,
                    home.position.yRot, home.position.xRot);
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.teleported", "Teleported to home %s.", name
            ));
            ModConfigs.DebugLog.info("Player {} teleported to home {} at dimension={}, x={}, y={}, z={}",
                    player.getName().getString(), name, home.position.dimension,
                    home.position.x, home.position.y, home.position.z);
            return 1;
        }
        return teleportToOtherHome(player, name); // 尝试作为他人家处理
    }


    private static int removeHome(ServerPlayer player, String name) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(name)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.not_found", "Home %s not found!", name
            ));
            return 0;
        }

        homes.remove(name);

        // 同时从公开家园列表中移除
        Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(player.getUUID().toString());
        if (ownerPublicHomes != null) {
            ownerPublicHomes.remove(name);
            if (ownerPublicHomes.isEmpty()) {
                publicHomesByOwner.remove(player.getUUID().toString());
            }
        }

        if (homes.isEmpty()) {
            playerHomes.remove(player.getUUID());
        }

        saveHomes();
        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.home.removed", "Home %s removed.", name
        ));
        ModConfigs.DebugLog.info("Player {} removed home {}", player.getName().getString(), name);
        return 1;
    }

    private static int renameHome(ServerPlayer player, String oldName, String newName) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(oldName)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.not_found", "Home %s not found!", oldName
            ));
            return 0;
        }
        if (homes.containsKey(newName)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.name_exists", "A home named %s already exists!", newName
            ));
            return 0;
        }

        Home home = homes.remove(oldName);
        homes.put(newName, home);

        // 更新公开家园列表中的名称（如果这个家园是公开的）
        Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(player.getUUID().toString());
        if (ownerPublicHomes != null && ownerPublicHomes.containsKey(oldName)) {
            PublicHomeInfo info = ownerPublicHomes.remove(oldName);
            // 使用三个参数的构造函数
            ownerPublicHomes.put(newName, new PublicHomeInfo(info.ownerUUID, info.ownerName, newName));
        }

        saveHomes();
        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.home.renamed", "Home %s renamed to %s.", oldName, newName
        ));
        ModConfigs.DebugLog.info("Player {} renamed home {} to {}", player.getName().getString(), oldName, newName);
        return 1;
    }


    private static int shareHome(ServerPlayer player, String name, ServerPlayer target) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(name)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.not_found", "Home %s not found!", name
            ));
            return 0;
        }
        Home home = homes.get(name);
        if (home.sharedPlayers.contains(target.getUUID())) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.already_shared", "Home %s is already shared with %s!", name, target.getName()
            ));
            return 0;
        }
        home.sharedPlayers.add(target.getUUID());
        saveHomes();
        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.home.shared", "Home %s shared with %s.", name, target.getName()
        ));
        target.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.home.shared_received", "%s shared their home %s with you.", player.getName(), name
        ));
        ModConfigs.DebugLog.info("Player {} shared home {} with {}", player.getName().getString(), name, target.getName().getString());
        return 1;
    }

    //unshareHome 方法
    private static int unshareHome(ServerPlayer player, String homeName, String targetPlayerName) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(homeName)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.not_found", "Home %s not found!", homeName
            ));
            return 0;
        }
        Home home = homes.get(homeName);
        if (targetPlayerName == null) {
            if (home.sharedPlayers.isEmpty()) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.unshare.no_players", "Home %s is not shared with anyone!", homeName
                ));
                return 0;
            }
            home.sharedPlayers.clear();
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.unshare.all", "Removed sharing of home %s for all players.", homeName
            ));
            saveHomes();
            return 1;
        } else {
            MinecraftServer server = player.getServer();
            Optional<GameProfile> profile = server.getProfileCache().get(targetPlayerName);
            if (profile.isEmpty()) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.unshare.player_not_found", "Player %s not found!", targetPlayerName
                ));
                return 0;
            }
            UUID targetUUID = profile.get().getId();
            if (!home.sharedPlayers.contains(targetUUID)) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.unshare.no_share", "Home %s is not shared with %s!", homeName, targetPlayerName
                ));
                return 0;
            }
            home.sharedPlayers.remove(targetUUID);
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.unshare.player", "Removed sharing of home %s with %s.", homeName, targetPlayerName
            ));
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetUUID);
            if (targetPlayer != null) {
                targetPlayer.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.unshare.notify", "%s has removed sharing of their home %s with you.", player.getName().getString(), homeName
                ));
            }
            saveHomes();
            return 1;
        }
    }


    private static int setPublicHome(ServerPlayer player, String homeName) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(homeName)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.not_found", "Home %s not found!", homeName
            ));
            return 0;
        }

        // 检查是否已经是公开的
        Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(player.getUUID().toString());
        if (ownerPublicHomes != null && ownerPublicHomes.containsKey(homeName)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.already_public", "Home %s is already public!", homeName
            ));
            return 0;
        }

        // 添加公开家园信息 - 使用三个参数的构造函数
        String ownerName = player.getName().getString();
        PublicHomeInfo info = new PublicHomeInfo(player.getUUID(), ownerName, homeName);

        // 使用玩家UUID作为外层键，家园名称作为内层键
        publicHomesByOwner.computeIfAbsent(player.getUUID().toString(), k -> new HashMap<>())
                .put(homeName, info);

        saveHomes();
        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.home.publicized", "Home %s is now public.", homeName
        ));
        ModConfigs.DebugLog.info("Player {} set home {} as public.", player.getName().getString(), homeName);
        return 1;
    }

    private static int setPrivateHome(ServerPlayer player, String homeName) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(homeName)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.not_found", "Home %s not found!", homeName
            ));
            return 0;
        }

        Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(player.getUUID().toString());
        if (ownerPublicHomes == null || !ownerPublicHomes.containsKey(homeName)) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.not_public", "Home %s is not public!", homeName
            ));
            return 0;
        }

        // 移除公开家园信息
        ownerPublicHomes.remove(homeName);
        if (ownerPublicHomes.isEmpty()) {
            publicHomesByOwner.remove(player.getUUID().toString());
        }

        saveHomes();
        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.home.privatized", "Home %s is no longer public.", homeName
        ));
        ModConfigs.DebugLog.info("Player {} set home {} as private.", player.getName().getString(), homeName);
        return 1;
    }

    private static int teleportToOtherHome(ServerPlayer player, String homeArg) {
        try {
            String[] parts = homeArg.split(":", 2);
            if (parts.length != 2) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.invalid_format", "Invalid format! Use playername:homename."
                ));
                return 0;
            }
            String ownerName = parts[0];
            String homeName = parts[1];

            Optional<GameProfile> profile = player.getServer().getProfileCache().get(ownerName);
            if (profile.isEmpty()) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.other_not_found", "Home %s not found or not accessible!", homeArg
                ));
                return 0;
            }
            UUID ownerUUID = profile.get().getId();

            // 检查是否是公开家园
            Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(ownerUUID.toString());
            boolean isPublic = ownerPublicHomes != null && ownerPublicHomes.containsKey(homeName);

            // 检查是否是分享的家园
            Map<String, Home> homes = playerHomes.get(ownerUUID);
            if (homes == null || !homes.containsKey(homeName)) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.other_not_found", "Home %s not found or not accessible!", homeArg
                ));
                return 0;
            }
            Home home = homes.get(homeName);

            if (!isPublic && !home.sharedPlayers.contains(player.getUUID())) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.other_not_found", "Home %s not found or not accessible!", homeArg
                ));
                return 0;
            }

            ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, home.position.dimension);
            ServerLevel targetLevel = player.getServer().getLevel(dimensionKey);
            if (targetLevel == null) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.invalid_dimension", "Invalid dimension for home %s!", homeArg
                ));
                return 0;
            }

            recordLastPosition(player);
            double homeX = home.position.x, homeY = home.position.y, homeZ = home.position.z;
            if (ModConfigs.SAFE_TELEPORT.get()) {
                BlockPos safePos = ModUtils.findSafeTeleportPosition(targetLevel, homeX, homeY, homeZ);
                homeX = safePos.getX() + 0.5;
                homeY = safePos.getY();
                homeZ = safePos.getZ() + 0.5;
            }
            ModUtils.teleportWithAllChains(player, targetLevel, homeX, homeY, homeZ,
                    home.position.yRot, home.position.xRot);
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.home.other_teleported", "Teleported to %s's home %s.", ownerName, homeName
            ));
            ModConfigs.DebugLog.info("Player {} teleported to {}'s home {}", player.getName().getString(), ownerName, homeName);
            return 1;
        } catch (Exception e) {
            ModConfigs.DebugLog.error("Error executing /home otherhome {}: {}", homeArg, e.getMessage());
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.error", "An unexpected error occurred while executing the command."
            ));
            return 0;
        }
    }

    private static int sendInvite(CommandSourceStack source, ServerPlayer sender, ServerPlayer target, String homeName) {
        if (sender.getUUID().equals(target.getUUID())) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.self", "You cannot invite yourself!"));
            return 0;
        }

        String cooldownKey = sender.getUUID() + ":" + target.getUUID();
        long currentTime = System.currentTimeMillis();
        long lastInviteTime = inviteCooldowns.getOrDefault(cooldownKey, 0L);
        long cooldownMs = ModConfigs.HOME_INVITE_COOLDOWN.get() * 1000L;

        // 清理已过期的冷却记录，避免长期运行后无限增长
        inviteCooldowns.entrySet().removeIf(entry -> currentTime - entry.getValue() > Math.max(cooldownMs, 60_000L));

        if (currentTime - lastInviteTime < cooldownMs) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.cooldown", "Please wait before sending another invite!"));
            return 0;
        }

        Map<String, Home> senderHomes = playerHomes.get(sender.getUUID());
        if (senderHomes == null || !senderHomes.containsKey(homeName)) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.no_home", "You don't have a home with that name!"));
            return 0;
        }

        // 检查是否已有相同邀请，如果存在则重置超时时间
        List<InviteRequest> existingInvites = pendingInvites.computeIfAbsent(target.getUUID(), k -> new ArrayList<>());
        boolean foundExisting = false;
        for (int i = 0; i < existingInvites.size(); i++) {
            InviteRequest req = existingInvites.get(i);
            if (req.senderUUID.equals(sender.getUUID()) && req.homeName.equals(homeName)) {
                existingInvites.set(i, new InviteRequest(sender.getUUID(), sender.getName().getString(), homeName));
                foundExisting = true;
                break;
            }
        }

        if (!foundExisting) {
            existingInvites.add(new InviteRequest(sender.getUUID(), sender.getName().getString(), homeName));
        }

        inviteCooldowns.put(cooldownKey, currentTime);

        MutableComponent senderMsg = Component.literal("")
                .append(ModUtils.translateWithFallback("command.tpatools.home.invite.send_success", "An invitation to visit the home '%s' has been sent to %s.", target.getName().getString(), homeName)
                        .withStyle(ChatFormatting.GRAY))
                .append(ModChatMenus.createButton(
                        " [取消]",
                        "取消此邀请",
                        "/home invite cancel " + target.getName().getString(),
                        ChatFormatting.RED,
                        true
                ));

        sender.sendSystemMessage(senderMsg);

        MutableComponent targetMsg = ModUtils.translateWithFallback("command.tpatools.home.invite.receive_info", "%s invites you to visit their home '%s'.", sender.getName().getString(), homeName)
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("  "))
                .append(ModChatMenus.createI18nButton(
                        "menu.tpatools.home.button.accept",
                        "menu.tpatools.home.hover.accept",
                        "/home invite accept " + sender.getName().getString(),
                        ChatFormatting.GREEN,
                        true
                ))
                .append(Component.literal(" "))
                .append(ModChatMenus.createI18nButton(
                        "menu.tpatools.home.button.deny",
                        "menu.tpatools.home.hover.deny",
                        "/home invite deny " + sender.getName().getString(),
                        ChatFormatting.RED,
                        true
                ));

        target.sendSystemMessage(targetMsg);

        // 设置超时
        long timeoutMs = ModConfigs.HOME_INVITE_TIMEOUT.get() * 1000L;
        scheduler.schedule(() -> {
            UUID targetUUID = target.getUUID();
            List<InviteRequest> invites = pendingInvites.get(targetUUID);
            if (invites != null) {
                // 找到即将超时的邀请请求，并发送超时提醒
                for (Iterator<InviteRequest> iterator = invites.iterator(); iterator.hasNext();) {
                    InviteRequest req = iterator.next();
                    if (req.senderUUID.equals(sender.getUUID()) && req.homeName.equals(homeName)) {
                        // 发送超时提醒给发送方
                        ServerPlayer timeoutSender = sender.getServer().getPlayerList().getPlayer(sender.getUUID());
                        if (timeoutSender != null) {
                            timeoutSender.sendSystemMessage(ModUtils.translateWithFallback(
                                    "command.tpatools.home.invite.timeout", 
                                    "Your invite to %s for home '%s' has timed out.", 
                                    target.getName(), homeName));
                        }

                        // 发送超时提醒给接收方
                        ServerPlayer timeoutTarget = target.getServer().getPlayerList().getPlayer(target.getUUID());
                        if (timeoutTarget != null) {
                            timeoutTarget.sendSystemMessage(ModUtils.translateWithFallback(
                                    "command.tpatools.home.invite.timeout_received", 
                                    "The invite from %s for home '%s' has timed out.", 
                                    sender.getName(), homeName));
                        }

                        // 移除超时的邀请
                        iterator.remove();
                        break;
                    }
                }
                if (invites.isEmpty()) {
                    pendingInvites.remove(targetUUID);
                }
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        return 1;
    }

    private static int cancelInvite(CommandSourceStack source, ServerPlayer sender, ServerPlayer target) {
        List<InviteRequest> invites = pendingInvites.get(target.getUUID());
        if (invites == null) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.no_pending", "No pending invites to cancel."));
            return 0;
        }

        invites.removeIf(req -> req.senderUUID.equals(sender.getUUID()));
        if (invites.isEmpty()) {
            pendingInvites.remove(target.getUUID());
        }

        source.sendSuccess(() -> ModUtils.translateWithFallback("command.tpatools.home.invite.cancelled", "Invite to %s has been cancelled.", target.getName().getString()), true);
        return 1;
    }

    private static int cancelAllInvites(CommandSourceStack source, ServerPlayer sender) {
        boolean cancelledAny = false;

        for (Iterator<Map.Entry<UUID, List<InviteRequest>>> it = pendingInvites.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, List<InviteRequest>> entry = it.next();
            List<InviteRequest> invites = entry.getValue();
            int before = invites.size();
            invites.removeIf(req -> req.senderUUID.equals(sender.getUUID()));
            if (invites.size() < before) {
                cancelledAny = true;
            }
            if (invites.isEmpty()) {
                it.remove();
            }
        }

        if (cancelledAny) {
            source.sendSuccess(() -> ModUtils.translateWithFallback("command.tpatools.home.invite.cancelled_all", "All pending invites have been cancelled."), true);
        } else {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.no_pending", "No pending invites to cancel."));
        }

        return cancelledAny ? 1 : 0;
    }

    private static int acceptInvite(CommandSourceStack source, ServerPlayer receiver, ServerPlayer sender) {
        List<InviteRequest> invites = pendingInvites.get(receiver.getUUID());
        if (invites == null) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.no_from_player", "No pending invites from that player."));
            return 0;
        }

        InviteRequest foundInvite = null;
        for (InviteRequest req : invites) {
            if (req.senderUUID.equals(sender.getUUID())) {
                foundInvite = req;
                break;
            }
        }

        if (foundInvite == null) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.no_from_player", "No pending invite from that player."));
            return 0;
        }

        // 传送玩家
        Map<String, Home> senderHomes = playerHomes.get(foundInvite.senderUUID);
        if (senderHomes == null) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.no_home_exists", "The sender's homes no longer exist!"));
            return 0;
        }

        Home senderHome = senderHomes.get(foundInvite.homeName);
        if (senderHome == null) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.home_no_exists", "The home no longer exists!"));
            return 0;
        }

        BackHandler.recordPosition(receiver);
        final Home acceptedHome = senderHome;
        TeleportDelayManager.requestTeleport(receiver, () -> teleportPlayer(receiver,
                acceptedHome.position.x, acceptedHome.position.y, acceptedHome.position.z,
                acceptedHome.position.dimension));

        // 移除邀请
        invites.remove(foundInvite);
        if (invites.isEmpty()) {
            pendingInvites.remove(receiver.getUUID());
        }

        final String homeNameFinal = foundInvite.homeName;

        source.sendSuccess(() -> ModUtils.translateWithFallback("command.tpatools.home.invite.accepted", "Invite accepted! Teleporting to %s.", homeNameFinal), true);
        sender.sendSystemMessage(ModUtils.translateWithFallback("command.tpatools.home.invite.accepted_by", "Your teleport request was accepted by %s!", receiver.getName()));
        return 1;
    }

    private static int acceptLatestInvite(CommandSourceStack source, ServerPlayer receiver) {
        List<InviteRequest> invites = pendingInvites.get(receiver.getUUID());
        if (invites == null || invites.isEmpty()) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.no_invites", "No pending invites."));
            return 0;
        }

        // 找到最新的邀请
        InviteRequest latestInvite = invites.get(0);
        for (InviteRequest req : invites) {
            if (req.timestamp > latestInvite.timestamp) {
                latestInvite = req;
            }
        }

        // 传送玩家
        Map<String, Home> senderHomes = playerHomes.get(latestInvite.senderUUID);
        if (senderHomes == null) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.no_home_exists", "The sender's homes no longer exist!"));
            return 0;
        }

        Home senderHome = senderHomes.get(latestInvite.homeName);
        if (senderHome == null) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.home_no_exists", "The home no longer exists!"));
            return 0;
        }

        BackHandler.recordPosition(receiver);
        final Home acceptedHome = senderHome;
        TeleportDelayManager.requestTeleport(receiver, () -> teleportPlayer(receiver,
                acceptedHome.position.x, acceptedHome.position.y, acceptedHome.position.z,
                acceptedHome.position.dimension));

        // 移除邀请
        invites.remove(latestInvite);
        if (invites.isEmpty()) {
            pendingInvites.remove(receiver.getUUID());
        }


        final String homeNameFinal = latestInvite.homeName;

        source.sendSuccess(() -> ModUtils.translateWithFallback("command.tpatools.home.invite.accepted", "Invite accepted! Teleporting to %s.", homeNameFinal), true);
        ServerPlayer latestInviteSender = receiver.getServer().getPlayerList().getPlayer(latestInvite.senderUUID);
        if (latestInviteSender != null) {
            latestInviteSender.sendSystemMessage(ModUtils.translateWithFallback("command.tpatools.home.invite.accepted_by", "Your teleport request was accepted by %s!", receiver.getName()));
        }

        return 1;
    }

    private static int denyInvite(CommandSourceStack source, ServerPlayer receiver, ServerPlayer sender) {
        List<InviteRequest> invites = pendingInvites.get(receiver.getUUID());
        if (invites == null) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.no_from_player", "No pending invites from that player."));
            return 0;
        }

        InviteRequest foundInvite = null;
        for (InviteRequest req : invites) {
            if (req.senderUUID.equals(sender.getUUID())) {
                foundInvite = req;
                break;
            }
        }

        if (foundInvite == null) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.no_from_player", "No pending invite from that player."));
            return 0;
        }

        // 移除邀请
        invites.remove(foundInvite);
        if (invites.isEmpty()) {
            pendingInvites.remove(receiver.getUUID());
        }

        final String homeNameFinal = foundInvite.homeName;

        source.sendSuccess(() -> ModUtils.translateWithFallback("command.tpatools.home.invite.denied", "Invite from %s denied.",
                sender.getName().getString()), true);
        sender.sendSystemMessage(ModUtils.translateWithFallback("command.tpatools.tpa.denied_by", "%s denied your invite to %s.",
                receiver.getName(), homeNameFinal));

        return 1;
    }

    private static int denyLatestInvite(CommandSourceStack source, ServerPlayer receiver) {
        List<InviteRequest> invites = pendingInvites.get(receiver.getUUID());
        if (invites == null || invites.isEmpty()) {
            source.sendFailure(ModUtils.translateWithFallback("command.tpatools.home.invite.no_invites", "No pending invites."));
            return 0;
        }

        // 找到最新的邀请
        InviteRequest latestInvite = invites.get(0);
        for (InviteRequest req : invites) {
            if (req.timestamp > latestInvite.timestamp) {
                latestInvite = req;
            }
        }

        // 移除邀请
        invites.remove(latestInvite);
        if (invites.isEmpty()) {
            pendingInvites.remove(receiver.getUUID());
        }

        final String homeNameFinal = latestInvite.homeName;
        final String senderNameFinal = latestInvite.senderName;

        source.sendSuccess(() -> ModUtils.translateWithFallback("command.tpatools.home.invite.denied", "Invite from %s denied.",
                senderNameFinal), true);
        ServerPlayer latestInviteSender = receiver.getServer().getPlayerList().getPlayer(latestInvite.senderUUID);
        if (latestInviteSender != null) {
            latestInviteSender.sendSystemMessage(ModUtils.translateWithFallback("command.tpatools.tpa.denied_by", "%s denied your invite to %s.", receiver.getName(), homeNameFinal));
        }

        return 1;
    }

    private static void teleportPlayer(ServerPlayer player, double x, double y, double z, ResourceLocation dimension) {
        ServerLevel targetLevel = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (targetLevel != null) {
            // 使用支持骑乘链和拴绳链的传送方法
            double tx = x, ty = y, tz = z;
            if (ModConfigs.SAFE_TELEPORT.get()) {
                BlockPos safePos = ModUtils.findSafeTeleportPosition(targetLevel, tx, ty, tz);
                tx = safePos.getX() + 0.5;
                ty = safePos.getY();
                tz = safePos.getZ() + 0.5;
            }
            ModUtils.teleportWithAllChains(player, targetLevel, tx, ty, tz, player.getYRot(), player.getXRot());
        } else {
            player.sendSystemMessage(ModUtils.translateWithFallback("command.tpatools.home.invite.teleport_failed", "Failed to teleport: invalid dimension.").withStyle(ChatFormatting.RED));
        }
    }

    /**
     * /home tp 的前置校验：自己的家直接放行；"玩家名:家名" 形式则校验公开/分享权限。
     */
    private static boolean validateHomeAccessible(ServerPlayer player, String homeName) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes != null && homes.containsKey(homeName)) {
            Home home = homes.get(homeName);
            ServerLevel targetLevel = player.getServer().getLevel(ResourceKey.create(
                    Registries.DIMENSION, home.position.dimension));
            if (targetLevel == null) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.invalid_dimension",
                        "Invalid dimension for home %s!",
                        homeName
                ));
                return false;
            }
            return true;
        }
        // 不是自己的家：支持用 玩家名:家名 访问公开或分享给自己的家
        if (homeName.indexOf(':') >= 0) {
            return validateOtherHomeExists(player, homeName);
        }
        player.sendSystemMessage(ModUtils.translateWithFallback(
                "command.tpatools.home.not_found",
                "Home %s not found!",
                homeName
        ));
        return false;
    }

    /**
     * /home otherhome 的前置校验：目标家必须存在，且是公开的或已分享给该玩家。
     */
    private static boolean validateOtherHomeExists(ServerPlayer player, String homeArg) {
        try {
            String[] parts = homeArg.split(":", 2);
            if (parts.length != 2) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.invalid_format",
                        "Invalid format! Use playername:homename."
                ));
                return false;
            }
            String ownerName = parts[0];
            String homeName = parts[1];

            Optional<GameProfile> profile = player.getServer().getProfileCache().get(ownerName);
            if (profile.isEmpty()) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.other_not_found",
                        "Home %s not found or not accessible!",
                        homeArg
                ));
                return false;
            }
            UUID ownerUUID = profile.get().getId();

            Map<String, PublicHomeInfo> ownerPublicHomes = publicHomesByOwner.get(ownerUUID.toString());
            boolean isPublic = ownerPublicHomes != null && ownerPublicHomes.containsKey(homeName);

            Map<String, Home> homes = playerHomes.get(ownerUUID);
            if (homes == null || !homes.containsKey(homeName)) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.other_not_found",
                        "Home %s not found or not accessible!",
                        homeArg
                ));
                return false;
            }
            Home home = homes.get(homeName);
            if (!isPublic && !home.sharedPlayers.contains(player.getUUID())) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.other_not_found",
                        "Home %s not found or not accessible!",
                        homeArg
                ));
                return false;
            }
            ServerLevel targetLevel = player.getServer().getLevel(ResourceKey.create(
                    Registries.DIMENSION, home.position.dimension));
            if (targetLevel == null) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.invalid_dimension",
                        "Invalid dimension for home %s!",
                        homeArg
                ));
                return false;
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Error validating other home {}: {}", homeArg, e.getMessage());
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.error",
                    "An unexpected error occurred while executing the command."
            ));
            return false;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        scheduler.shutdown();
    }
}