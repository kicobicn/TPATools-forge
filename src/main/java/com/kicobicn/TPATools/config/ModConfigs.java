package com.kicobicn.TPATools.config;

import com.kicobicn.TPATools.chat.ModChatMenus;
import com.kicobicn.TPATools.util.ModUtils;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import com.kicobicn.TPATools.Commands.TPAHandler;

public class ModConfigs {
    private static final Logger LOGGER = LogManager.getLogger("TPAtools");

    // 配置项定义
    public static final ForgeConfigSpec CONFIG;
    public static final ForgeConfigSpec.LongValue COOLDOWN_TIME;
    public static final ForgeConfigSpec.IntValue TIMEOUT_TICKS;
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_LANGUAGE;
    public static final ForgeConfigSpec.IntValue MAX_HOMES;
    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE;
    public static final ForgeConfigSpec.ConfigValue<Integer> HOME_INVITE_COOLDOWN;
    public static final ForgeConfigSpec.ConfigValue<Integer> HOME_INVITE_TIMEOUT;
    public static final ForgeConfigSpec.ConfigValue<Boolean> ALLOW_TELEPORT_RIDE_ENTITY;
    public static final ForgeConfigSpec.ConfigValue<Boolean> SAFE_TELEPORT;
    public static final ForgeConfigSpec.ConfigValue<Boolean> TELEPORT_NEED_TIME;
    public static final ForgeConfigSpec.IntValue TELEPORT_DELAY_TIME;

    public static final ForgeConfigSpec.ConfigValue<String> DATABASE_TYPE;
    public static final ForgeConfigSpec.ConfigValue<String> MYSQL_HOST;
    public static final ForgeConfigSpec.ConfigValue<Integer> MYSQL_PORT;
    public static final ForgeConfigSpec.ConfigValue<String> MYSQL_DATABASE;
    public static final ForgeConfigSpec.ConfigValue<String> MYSQL_USERNAME;
    public static final ForgeConfigSpec.ConfigValue<String> MYSQL_PASSWORD;

    public static final ForgeConfigSpec.ConfigValue<Integer> RTP_SCOPE;
    public static final ForgeConfigSpec.ConfigValue<Integer> RTP_COOLDOWN_TIME;
    public static final ForgeConfigSpec.ConfigValue<Integer> RTP_MAX_ATTEMPTS;
    public static final ForgeConfigSpec.ConfigValue<Integer> RTP_MIN_Y;
    public static final ForgeConfigSpec.ConfigValue<Integer> RTP_MAX_Y;

    public static final ForgeConfigSpec.ConfigValue<Integer> MAX_WARP_COUNT;

    //配置路径检查
    public static Path getConfigDir() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve("tpatools");
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                DebugLog.info("Created config directory: {}", configDir.toString());
            }
        } catch (IOException e) {
            DebugLog.error("Failed to create config directory: {}", e.getMessage());
        }
        return configDir;
    }

    public static boolean checkCommandPermission(CommandSourceStack source, String command) {
        boolean needOp = commandPermissions.getOrDefault(command, false);
        if (needOp) {
            return source.hasPermission(2); // 需要OP权限
        } else {
            return true; // 不需要OP权限
        }
    }

    // 命令权限状态
    public static final Map<String, Boolean> commandPermissions = new HashMap<>();

    // Tab补全提供器
    private static final SuggestionProvider<CommandSourceStack> COMMAND_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("tpa").suggest("home").suggest("grave").suggest("back").suggest("rtp").suggest("warp").buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> BOOLEAN_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("true").suggest("false").buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> LANGUAGE_SUGGESTIONS = (context, builder) -> {
        return builder.suggest("en_us").suggest("zh_cn").suggest("fr_fr").suggest("pt_br").suggest("es_es").suggest("zh_cn_cute").buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> RTP_SCOPE_SUGGESTIONS = (context, builder) -> {
        builder.suggest("10000");
        builder.suggest("20000");
        builder.suggest("50000");
        builder.suggest("100000");
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> TIME_SUGGESTIONS = (context, builder) -> {
        builder.suggest("10");
        builder.suggest("30");
        builder.suggest("60");
        builder.suggest("120");
        builder.suggest("300");
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> MAX_WARP_COUNT_SUGGESTIONS = (context, builder) -> {
        builder.suggest("1");
        builder.suggest("2");
        builder.suggest("3");
        builder.suggest("4");
        builder.suggest("5");
        return builder.buildFuture();
    };

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("tpa");
        COOLDOWN_TIME = builder.comment("Cooldown time for TPA commands in milliseconds")
                .defineInRange("cooldown", 3 * 1000L, 0, Long.MAX_VALUE);
        TIMEOUT_TICKS = builder.comment("Timeout for TPA requests in ticks")
                .defineInRange("timeout", 30 * 20, 0, Integer.MAX_VALUE);
        DEFAULT_LANGUAGE = builder.comment("Default language for messages (e.g., 'en_us', 'zh_cn')")
                .define("language", "zh_cn");
        MAX_HOMES = builder.comment("Maximum number of homes per player")
                .defineInRange("max_homes", 2, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.push("debug");
        DEBUG_MODE = builder.comment("Enable debug logging for detailed output")
                .define("debugMode", false); // 默认关闭
        builder.pop();

        HOME_INVITE_COOLDOWN = builder
                .comment("Cooldown time in seconds between home invites")
                .defineInRange("homeInviteCooldown", 0, 0, 300);

        HOME_INVITE_TIMEOUT = builder
                .comment("Timeout time in seconds for home invites")
                .defineInRange("homeInviteTimeout", 30, 1, 300);

        builder.push("teleport");
        ALLOW_TELEPORT_RIDE_ENTITY = builder
                .comment("Allow teleporting ridden entities with players")
                .define("allowTeleportRideEntity", false);
        SAFE_TELEPORT = builder
                .comment("If teleport destination is blocked, search for a safe nearby position")
                .define("safeTeleport", true);
        TELEPORT_NEED_TIME = builder
                .comment("Require players to stand still for a while before the teleport actually happens")
                .define("teleportNeedTime", false);
        TELEPORT_DELAY_TIME = builder
                .comment("Seconds players must stand still before the teleport happens")
                .defineInRange("teleportDelayTime", 3, 1, 60);
        builder.pop();

        builder.push("database");
        DATABASE_TYPE = builder.comment("Storage type: 'json' or 'mysql'")
                .define("type", "json");
        MYSQL_HOST = builder.comment("MySQL server host")
                .define("host", "localhost");
        MYSQL_PORT = builder.comment("MySQL server port")
                .defineInRange("port", 3306, 1, 65535);
        MYSQL_DATABASE = builder.comment("MySQL database name")
                .define("database", "tpatools");
        MYSQL_USERNAME = builder.comment("MySQL username")
                .define("username", "root");
        MYSQL_PASSWORD = builder.comment("MySQL password")
                .define("password", "");
        builder.pop();

        builder.push("rtp");
        RTP_SCOPE = builder.comment("RTP teleport range in blocks (default: 100000)")
                .defineInRange("scope", 100000, 1000, 1000000);
        RTP_COOLDOWN_TIME = builder.comment("RTP cooldown time in seconds (default: 30)")
                .defineInRange("cooldown_time", 30, 0, 3600);
        RTP_MAX_ATTEMPTS = builder.comment("Maximum attempts to find a safe location (default: 50)")
                .defineInRange("max_attempts", 50, 1, 200);
        RTP_MIN_Y = builder.comment("Minimum Y level for RTP (default: -64)")
                .defineInRange("min_y", -64, -64, 320);
        RTP_MAX_Y = builder.comment("Maximum Y level for RTP (default: 320)")
                .defineInRange("max_y", 320, -64, 320);
        builder.pop();

        builder.push("warp");
        MAX_WARP_COUNT = builder.comment("Maximum number of warps per player")
                .defineInRange("max_warps", 3, 1, 100);
        builder.pop();

        CONFIG = builder.build();
    }

    //调试模式
    public static boolean isDebugEnabled() {
        return DEBUG_MODE.get();
    }

    public static class DebugLog {
        public static void info(String message, Object... args) {
            if (isDebugEnabled()) {
                LOGGER.info("[DEBUG INFO] " + message, args);
            }
        }

        public static void warn(String message, Object... args) {
            if (isDebugEnabled()) {
                LOGGER.warn("[DEBUG WARN] " + message, args);
            }
        }

        public static void error(String message, Object... args) {
            if (isDebugEnabled()) {
                LOGGER.error("[DEBUG ERROR] " + message, args);
            }
        }

        public static void log(String message, Object... args) {
            info(message, args);
        }

    }

    //初始化命令权限
    public static void initCommandPermissions() {
        commandPermissions.putIfAbsent("tpa", false);
        commandPermissions.putIfAbsent("home", false);
        commandPermissions.putIfAbsent("grave", false);
        commandPermissions.putIfAbsent("back", false);
        commandPermissions.putIfAbsent("rtp", false);
        commandPermissions.putIfAbsent("warp", false);
        commandPermissions.putIfAbsent("debug", false);
    }

    //注册tpatool管理命令
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tpatools")
                        .executes(context -> {  // 无参数时显示设置菜单
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ModChatMenus.ConfigMenus.showSettingsMenu(player);
                            return 1;
                        })
                        .then(Commands.literal("configs")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ModChatMenus.ConfigMenus.showConfigsMenu(player);
                                    return 1;
                                })
                                .then(Commands.literal("setlanguage")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showSetLanguageMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("lang", StringArgumentType.string())
                                                .suggests(LANGUAGE_SUGGESTIONS)
                                                .executes(context -> {
                                                    String lang = StringArgumentType.getString(context, "lang");
                                                    if (lang.equals("en_us") || lang.equals("zh_cn") || lang.equals("fr_fr") || lang.equals("pt_br") || lang.equals("es_es") || lang.equals("zh_cn_cute")) {
                                                        DEFAULT_LANGUAGE.set(lang);
                                                        DEFAULT_LANGUAGE.save();
                                                        ModUtils.loadTranslations(lang);
                                                        context.getSource().sendSuccess(
                                                                () -> ModUtils.translateWithFallback("command.tpatools.setlanguage.success", "Language set to %s.", lang),
                                                                true
                                                        );
                                                        DebugLog.info("Language switched to {} by {}", lang, context.getSource().getDisplayName().getString());
                                                        return 1;
                                                    }
                                                    context.getSource().sendFailure(
                                                            ModUtils.translateWithFallback("command.tpatools.setlanguage.invalid", "Invalid language.")
                                                    );
                                                    return 0;
                                                })))
                                .then(Commands.literal("setmaxhome")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showSetMaxHomeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(context -> {
                                                    int count = IntegerArgumentType.getInteger(context, "count");
                                                    MAX_HOMES.set(count);
                                                    MAX_HOMES.save();
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback("command.tpatools.setmaxhome.success", "Maximum homes set to %s.", count),
                                                            true
                                                    );
                                                    DebugLog.info("Max homes set to {} by {}", count, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("needop")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showNeedOpMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("command", StringArgumentType.string())
                                                .suggests(COMMAND_SUGGESTIONS)
                                                .then(Commands.argument("enable", StringArgumentType.string())
                                                        .suggests(BOOLEAN_SUGGESTIONS)
                                                        .executes(context -> {
                                                            String command = StringArgumentType.getString(context, "command");
                                                            String enableStr = StringArgumentType.getString(context, "enable");
                                                            boolean enable = enableStr.equalsIgnoreCase("true");
                                                            if (!Arrays.asList("tpa", "home", "grave", "back", "warp", "rtp").contains(command)) {
                                                                context.getSource().sendFailure(
                                                                        ModUtils.translateWithFallback("command.tpatools.needop.invalid_command", "Invalid command. Use 'tpa', 'home', 'grave', 'back', 'warp', or 'rtp'.")
                                                                );
                                                                return 0;
                                                            }
                                                            commandPermissions.put(command, enable);
                                                            TPAHandler.saveCommandPermissions();
                                                            context.getSource().sendSuccess(
                                                                    () -> ModUtils.translateWithFallback(
                                                                            enable ? "command.tpatools.needop.success_enabled" : "command.tpatools.needop.success_disabled",
                                                                            enable ? "%s commands now require OP permission." : "%s commands now do not require OP permission.",
                                                                            command
                                                                    ),
                                                                    true
                                                            );
                                                            DebugLog.info("{} commands set to {} OP by {}", command, enable ? "require" : "not require", context.getSource().getDisplayName().getString());
                                                            return 1;
                                                        }))))
                                .then(Commands.literal("tpacdtime")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showTPACDTimeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("time", IntegerArgumentType.integer(0))
                                                .suggests(TIME_SUGGESTIONS)
                                                .executes(context -> {
                                                    int time = IntegerArgumentType.getInteger(context, "time");
                                                    COOLDOWN_TIME.set((long) time * 1000); // 转换为毫秒
                                                    COOLDOWN_TIME.save();
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatools.tpacdtime.success",
                                                                    "TPA cooldown time set to %d seconds.",
                                                                    time
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("TPA cooldown time set to {} seconds by {}",
                                                            time, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("tpawaittime")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showTPAWaitTimeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("time", IntegerArgumentType.integer(0))
                                                .suggests(TIME_SUGGESTIONS)
                                                .executes(context -> {
                                                    int time = IntegerArgumentType.getInteger(context, "time");
                                                    TIMEOUT_TICKS.set(time * 20); // 转换为ticks (1秒=20ticks)
                                                    TIMEOUT_TICKS.save();
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatools.tpawaittime.success",
                                                                    "TPA wait time set to %d seconds.",
                                                                    time
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("TPA wait time set to {} seconds by {}",
                                                            time, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                                                                }))
                                                                                )
                                .then(Commands.literal("homeinviteovertime")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showHomeInviteOverTimeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("time", IntegerArgumentType.integer(1))
                                                .suggests(TIME_SUGGESTIONS)
                                                .executes(context -> {
                                                    int time = IntegerArgumentType.getInteger(context, "time");
                                                    HOME_INVITE_TIMEOUT.set(time);
                                                    HOME_INVITE_TIMEOUT.save();
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatools.home.inviteovertime.success",
                                                                    "Home invite timeout time set to %d seconds.",
                                                                    time
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("Home invite timeout time set to {} seconds by {}",
                                                            time, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("homeinvitecdtime")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showHomeInviteCDTimeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("time", IntegerArgumentType.integer(0))
                                                .suggests(TIME_SUGGESTIONS)
                                                .executes(context -> {
                                                    int time = IntegerArgumentType.getInteger(context, "time");
                                                    HOME_INVITE_COOLDOWN.set(time);
                                                    HOME_INVITE_COOLDOWN.save();
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatools.home.invitecdtime.success",
                                                                    "Home invite cooldown time set to %d seconds.",
                                                                    time
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("Home invite cooldown time set to {} seconds by {}",
                                                            time, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("teleportneedtime")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showTeleportNeedTimeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("enable", StringArgumentType.string())
                                                .suggests(BOOLEAN_SUGGESTIONS)
                                                .executes(context -> {
                                                    String enableStr = StringArgumentType.getString(context, "enable");
                                                    boolean enable = enableStr.equalsIgnoreCase("true");
                                                    TELEPORT_NEED_TIME.set(enable);
                                                    TELEPORT_NEED_TIME.save();
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    enable ? "command.tpatools.config.teleportneedtime.enabled" : "command.tpatools.config.teleportneedtime.disabled",
                                                                    enable ? "Teleport delay enabled. Players must stand still before teleporting."
                                                                            : "Teleport delay disabled. Players will teleport immediately."
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("Teleport delay {} by {}", enable ? "enabled" : "disabled",
                                                            context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("teleportdelaytime")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showTeleportDelayTimeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("time", IntegerArgumentType.integer(1))
                                                .suggests(TIME_SUGGESTIONS)
                                                .executes(context -> {
                                                    int time = IntegerArgumentType.getInteger(context, "time");
                                                    TELEPORT_DELAY_TIME.set(time);
                                                    TELEPORT_DELAY_TIME.save();
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatools.config.teleportdelaytime.success",
                                                                    "Teleport delay time set to %d seconds.",
                                                                    time
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("Teleport delay time set to {} seconds by {}",
                                                            time, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("safeteleport")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showSafeTeleportMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("enable", StringArgumentType.string())
                                                .suggests(BOOLEAN_SUGGESTIONS)
                                                .executes(context -> {
                                                    String enableStr = StringArgumentType.getString(context, "enable");
                                                    boolean enable = enableStr.equalsIgnoreCase("true");
                                                    SAFE_TELEPORT.set(enable);
                                                    SAFE_TELEPORT.save();
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    enable ? "command.tpatools.config.safeteleport.enabled" : "command.tpatools.config.safeteleport.disabled",
                                                                    enable ? "Safe teleport enabled." : "Safe teleport disabled."
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("Safe teleport {} by {}", enable ? "enabled" : "disabled", context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("allowteleportrideentity")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showAllowTeleportRideEntityMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("enable", StringArgumentType.string())
                                                .suggests(BOOLEAN_SUGGESTIONS)
                                                .executes(context -> {
                                                    String enableStr = StringArgumentType.getString(context, "enable");
                                                    boolean enable = enableStr.equalsIgnoreCase("true");
                                                    ALLOW_TELEPORT_RIDE_ENTITY.set(enable);
                                                    ALLOW_TELEPORT_RIDE_ENTITY.save();
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    enable ? "command.tpatools.config.allowteleportrideentity.enabled" : "command.tpatools.config.allowteleportrideentity.disabled",
                                                                    enable ? "Ride entity teleport enabled." : "Ride entity teleport disabled."
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("Ride entity teleport {} by {}", enable ? "enabled" : "disabled", context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("rtpcdtime")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showRTPCooldownTimeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("time", IntegerArgumentType.integer(0))
                                                .suggests(TIME_SUGGESTIONS)
                                                .executes(context -> {
                                                    int time = IntegerArgumentType.getInteger(context, "time");
                                                    RTP_COOLDOWN_TIME.set(time);
                                                    RTP_COOLDOWN_TIME.save();
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatools.config.rtp.cooldown.success",
                                                                    "RTP cooldown time set to %d seconds.",
                                                                    time
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("RTP cooldown time set to {} seconds by {}",
                                                            time, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("rtpscope")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showRTPScopeMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("scope", IntegerArgumentType.integer(0))
                                                .suggests(RTP_SCOPE_SUGGESTIONS)
                                                .executes(context -> {
                                                    int scope = IntegerArgumentType.getInteger(context, "scope");
                                                    RTP_SCOPE.set(scope);
                                                    RTP_SCOPE.save();
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatools.config.rtp.scope.success",
                                                                    "RTP scope set to %d blocks.",
                                                                    scope
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("RTP scope set to {} blocks by {}",
                                                            scope, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                                .then(Commands.literal("setmaxwarpcount")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ModChatMenus.ConfigMenus.showMaxWarpCountMenu(player);
                                            return 1;
                                        })
                                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                                .suggests(MAX_WARP_COUNT_SUGGESTIONS)
                                                .executes(context -> {
                                                    int count = IntegerArgumentType.getInteger(context, "count");
                                                    MAX_WARP_COUNT.set(count);
                                                    MAX_WARP_COUNT.save();
                                                    context.getSource().sendSuccess(
                                                            () -> ModUtils.translateWithFallback(
                                                                    "command.tpatools.config.maxwarpcount.success",
                                                                    "Max warp count set to %d.",
                                                                    count
                                                            ),
                                                            true
                                                    );
                                                    DebugLog.info("Max warp count set to {} by {}",
                                                            count, context.getSource().getDisplayName().getString());
                                                    return 1;
                                                })))
                        )
                        .then(Commands.literal("debug")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ModChatMenus.ConfigMenus.showDebugMenu(player);
                                    return 1;
                                })
                                .then(Commands.argument("enable", StringArgumentType.string())
                                    .suggests(BOOLEAN_SUGGESTIONS)
                                    .executes(context -> {
                                        String enableStr = StringArgumentType.getString(context, "enable");
                                        boolean enable = enableStr.equalsIgnoreCase("true");
                                        DEBUG_MODE.set(enable);
                                        DEBUG_MODE.save();
                                        context.getSource().sendSuccess(
                                            () -> ModUtils.translateWithFallback(
                                                    enable ? "command.tpatools.debug.enabled" : "command.tpatools.debug.disabled",
                                                    enable ? "Debug mode enabled." : "Debug mode disabled."
                                            ),
                                            true
                                        );
                                        DebugLog.info("Debug mode {} by {}", enable ? "enabled" : "disabled", context.getSource().getDisplayName().getString());
                                        return 1;
                                    })))
                        .then(Commands.literal("about")
                                .requires(source -> source.hasPermission(0))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ModChatMenus.ConfigMenus.showAboutMenu(player);
                                    return 1;
                                })
                        )
        );
    }
}