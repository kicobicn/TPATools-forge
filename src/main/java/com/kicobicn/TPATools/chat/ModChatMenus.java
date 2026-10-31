package com.kicobicn.TPATools.chat;

import com.kicobicn.TPATools.Commands.HomeHandler;
import com.kicobicn.TPATools.Commands.WarpHandler;
import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.util.ModUtils;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModChatMenus {

    // 存储玩家的UI状态
    private static final Map<UUID, UIState> playerUIStates = new ConcurrentHashMap<>();

    // UI状态类
    private static class UIState {
        String currentMenu;
        List<String> menuData;

        public UIState(String currentMenu, List<String> menuData) {
            this.currentMenu = currentMenu;
            this.menuData = menuData;
        }
    }


    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 初始化玩家UI状态
            playerUIStates.put(player.getUUID(), new UIState("home", new ArrayList<>()));
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 清理玩家UI状态
            playerUIStates.remove(player.getUUID());
        }
    }

    public static class HomeMenus {

        public static void showHomeMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - Home ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.home.title.main", "        -Home menu -\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));

                    // 按钮
                    menu.append(createI18nButton("menu.tpatools.home.button.sethome",
                                    "menu.tpatools.home.hover.sethome",
                                    "/home set ",
                                    ChatFormatting.GREEN,
                                    false))
                            .append(Component.literal("\n"));
            
                    menu.append(createI18nButton("menu.tpatools.home.button.managehome",
                                    "menu.tpatools.home.hover.managehome",
                                    "/home list",
                                    ChatFormatting.AQUA,
                                    true))
                            .append(Component.literal(" "))
                            .append(createI18nButton("menu.tpatools.home.button.visitother",
                                    "menu.tpatools.home.hover.visitother",
                                    "/home otherlist",
                                    ChatFormatting.AQUA,
                                    true))
                            .append(Component.literal("\n"));
            
                    menu.append(createI18nButton("menu.tpatools.home.button.sharedouthomes",
                                    "menu.tpatools.home.hover.sharedouthomes",
                                    "/home sharelist out",
                                    ChatFormatting.AQUA,
                                    true))
                            .append(Component.literal(" "))
                            .append(createI18nButton("menu.tpatools.home.button.sharedinhomes",
                                    "menu.tpatools.home.hover.sharedinhomes",
                                    "/home sharelist in",
                                    ChatFormatting.AQUA,
                                    true))
                            .append(Component.literal("\n"));
            // 分割线
            menu.append(Component.literal("==============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showOwnHomesList(ServerPlayer player, int page) {
            Map<String, HomeHandler.Home> homes = HomeHandler.playerHomes.get(player.getUUID());
            if (homes == null || homes.isEmpty()) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.list_empty", "You have no homes set."));
                return;
            }

            List<String> homeNames = new ArrayList<>(homes.keySet());
            int pageSize = 5;
            int totalPages = (int) Math.ceil((double) homeNames.size() / pageSize);
            page = Math.max(0, Math.min(page, totalPages - 1));

            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - Home/list ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.home.title.homelist", "        - Your Current Homes -\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));

            // 显示当前页的家
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, homeNames.size());

            for (int i = startIndex; i < endIndex; i++) {
                String homeName = homeNames.get(i);
                HomeHandler.Home home = homes.get(homeName);

                menu.append(Component.literal("- " + homeName + "\n")
                        .withStyle(ChatFormatting.WHITE));

                String position = String.format("%s (x=%.2f, y=%.2f, z=%.2f)",
                        home.position.dimension, home.position.x, home.position.y, home.position.z);
                menu.append(Component.literal("  " + position + "\n")
                        .withStyle(ChatFormatting.GRAY));

                                // 按钮行
                                MutableComponent buttons = Component.literal(" ");
                                buttons.append(createI18nButton("menu.tpatools.home.button.teleport",
                                                "menu.tpatools.home.hover.teleport",
                                                "/home tp " + homeName,
                                                ChatFormatting.GREEN,
                                                true))
                                        .append(Component.literal(" "));
                                buttons.append(createI18nButton("menu.tpatools.home.button.rename",
                                                "menu.tpatools.home.hover.rename",
                                                "/home rename " + homeName + " ",
                                                ChatFormatting.YELLOW,
                                                false))
                                        .append(Component.literal(" "));

                                // 公开/私密按钮
                                Map<String, HomeHandler.PublicHomeInfo> publicHomes = HomeHandler.publicHomesByOwner.get(player.getUUID().toString());
                                boolean isPublic = publicHomes != null && publicHomes.containsKey(homeName);
                                String publicCommand = isPublic ? "/home private " + homeName : "/home public " + homeName;
                                String publicText = isPublic ? "menu.tpatools.home.button.setprivate" : "menu.tpatools.home.button.setpublic";
                                String publicHover = isPublic ? "menu.tpatools.home.hover.setprivate" : "menu.tpatools.home.hover.setpublic";
                                ChatFormatting publicColor = isPublic ? ChatFormatting.RED : ChatFormatting.GREEN;

                                buttons.append(createI18nButton(publicText,
                                                publicHover,
                                                publicCommand,
                                                publicColor,
                                                true))
                                        .append(Component.literal(" "));

                                // 分享按钮
                                int sharedCount = home.sharedPlayers.size();
                                buttons.append(createI18nButtonWithHoverArgs("menu.tpatools.home.button.share",
                                                "menu.tpatools.home.hover.share",
                                                "/home share " + homeName + " ",
                                                ChatFormatting.AQUA,
                                                false,
                                                new Object[]{sharedCount},
                                                new Object[]{sharedCount}))
                                        .append(Component.literal(" "));

                                buttons.append(createI18nButton("menu.tpatools.home.button.invite",
                                                "menu.tpatools.home.hover.invite",
                                                "/home invite invite " + homeName + " ",
                                                ChatFormatting.LIGHT_PURPLE,
                                                false))
                                        .append(Component.literal(" "));

                                buttons.append(createI18nButton("menu.tpatools.home.button.delete",
                                                "menu.tpatools.home.hover.delete",
                                                "/home remove " + homeName,
                                                ChatFormatting.RED,
                                                true))
                                        .append(Component.literal(" "));
                menu.append(buttons).append(Component.literal("\n"));
            }

            // 分页系统
            if (totalPages > 1) {
                menu.append(Component.literal("=============================\n")
                        .withStyle(ChatFormatting.GOLD));

                MutableComponent pagination = Component.literal(" ");
                pagination.append(createI18nButton("menu.tpatools.home.button.previouspage",
                                "menu.tpatools.home.hover.previouspage",
                                "/home list page " + Math.max(0, page - 1),
                                ChatFormatting.GRAY,
                                true))
                        .append(Component.literal(" - " + (page + 1) + "/" + totalPages + " - "))
                        .append(createI18nButton("menu.tpatools.home.button.nextpage",
                                "menu.tpatools.home.hover.nextpage",
                                "/home list page " + Math.min(totalPages - 1, page + 1),
                                ChatFormatting.GRAY,
                                true));

                menu.append(pagination).append(Component.literal("\n"));
            } else {
                menu.append(Component.literal("=============================\n")
                        .withStyle(ChatFormatting.GOLD));
            }

            player.sendSystemMessage(menu);
        }

        public static void showSharedOutList(ServerPlayer player, int page) {
            Map<String, HomeHandler.Home> homes = HomeHandler.playerHomes.get(player.getUUID());
            if (homes == null || homes.isEmpty()) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.sharelist.out_empty", "You have not shared any homes."));
                return;
            }

            List<String> sharedHomeNames = new ArrayList<>();
            for (Map.Entry<String, HomeHandler.Home> entry : homes.entrySet()) {
                if (!entry.getValue().sharedPlayers.isEmpty()) {
                    sharedHomeNames.add(entry.getKey());
                }
            }

            if (sharedHomeNames.isEmpty()) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.sharelist.out_empty", "You have not shared any homes."));
                return;
            }

            int pageSize = 5;
            int totalPages = (int) Math.ceil((double) sharedHomeNames.size() / pageSize);
            page = Math.max(0, Math.min(page, totalPages - 1));

            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - Home/sharelist/out =====\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.home.title.sharelist_out", "        - Homes You've Shared -\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));

            // 显示当前页的分享家
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, sharedHomeNames.size());

            for (int i = startIndex; i < endIndex; i++) {
                String homeName = sharedHomeNames.get(i);
                HomeHandler.Home home = homes.get(homeName);

                menu.append(Component.literal("- " + homeName + "\n")
                        .withStyle(ChatFormatting.WHITE));

                String position = String.format("%s (x=%.2f, y=%.2f, z=%.2f)",
                        home.position.dimension, home.position.x, home.position.y, home.position.z);
                menu.append(Component.literal("  " + position + "\n")
                        .withStyle(ChatFormatting.GRAY));

                // 显示分享的玩家
                List<String> sharedPlayerNames = new ArrayList<>();
                for (UUID sharedUUID : home.sharedPlayers) {
                    String playerName = player.getServer().getProfileCache().get(sharedUUID)
                            .map(GameProfile::getName).orElse("Unknown");
                    sharedPlayerNames.add(playerName);
                }
                menu.append(Component.literal("  已分享给：" + String.join(" ", sharedPlayerNames) + "\n")
                        .withStyle(ChatFormatting.GRAY));

                // 按钮行
                MutableComponent buttons = Component.literal("  ");
                buttons.append(createI18nButton("menu.tpatools.home.button.cancelallshare",
                                "menu.tpatools.home.hover.cancelallshare",
                                "/home unshare " + homeName,
                                ChatFormatting.RED,
                                true))
                        .append(Component.literal(" "));

                buttons.append(createI18nButton("menu.tpatools.home.button.cancelplayershare",
                                "menu.tpatools.home.hover.cancelplayershare",
                                "/home unshare " + homeName + " ",
                                ChatFormatting.YELLOW,
                                false))
                        .append(Component.literal("\n\n"));

                menu.append(buttons);
            }

            // 分页系统
            if (totalPages > 1) {
                menu.append(Component.literal("=============================\n")
                        .withStyle(ChatFormatting.GOLD));

                MutableComponent pagination = Component.literal(" ");
                pagination.append(createI18nButton("menu.tpatools.home.button.previouspage",
                                "menu.tpatools.home.hover.previouspage",
                                "/home sharelist out page " + Math.max(0, page - 1),
                                ChatFormatting.GRAY,
                                true))
                        .append(Component.literal(" - " + (page + 1) + "/" + totalPages + " - "))
                        .append(createI18nButton("menu.tpatools.home.button.nextpage",
                                "menu.tpatools.home.hover.nextpage",
                                "/home sharelist out page " + Math.min(totalPages - 1, page + 1),
                                ChatFormatting.GRAY,
                                true));

                menu.append(pagination).append(Component.literal("\n"));
            } else {
                menu.append(Component.literal("=============================\n")
                        .withStyle(ChatFormatting.GOLD));
            }

            player.sendSystemMessage(menu);
        }

        public static void showPublicHomesList(ServerPlayer player, int page) {
            List<HomeHandler.PublicHomeInfo> entries = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            // 1) 公开的家
            for (Map<String, HomeHandler.PublicHomeInfo> ownerHomes : HomeHandler.publicHomesByOwner.values()) {
                for (HomeHandler.PublicHomeInfo info : ownerHomes.values()) {
                    if (seen.add(info.ownerUUID + ":" + info.homeName)) {
                        entries.add(info);
                    }
                }
            }

            // 2) 别人分享给我的家（此前 otherlist 里看不到）
            MinecraftServer server = player.getServer();
            for (Map.Entry<UUID, Map<String, HomeHandler.Home>> ownerEntry : HomeHandler.playerHomes.entrySet()) {
                UUID ownerUUID = ownerEntry.getKey();
                if (ownerUUID.equals(player.getUUID())) {
                    continue;
                }
                ServerPlayer onlineOwner = server.getPlayerList().getPlayer(ownerUUID);
                String ownerName = onlineOwner != null
                        ? onlineOwner.getName().getString()
                        : ownerUUID.toString().substring(0, 8);
                for (Map.Entry<String, HomeHandler.Home> homeEntry : ownerEntry.getValue().entrySet()) {
                    if (!homeEntry.getValue().sharedPlayers.contains(player.getUUID())) {
                        continue;
                    }
                    if (seen.add(ownerUUID + ":" + homeEntry.getKey())) {
                        entries.add(new HomeHandler.PublicHomeInfo(ownerUUID, ownerName, homeEntry.getKey()));
                    }
                }
            }

            if (entries.isEmpty()) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.home.otherlist_empty", "No public or shared homes available."));
                return;
            }

            int pageSize = 5;
            int totalPages = (int) Math.ceil((double) entries.size() / pageSize);
            page = Math.max(0, Math.min(page, totalPages - 1));

            MutableComponent menu = Component.literal("");
            menu.append(Component.literal("====== TPATools - Home/otherlist ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.home.title.otherlist", "        - Public Homes Available -\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));

            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, entries.size());

            for (int i = startIndex; i < endIndex; i++) {
                HomeHandler.PublicHomeInfo info = entries.get(i);
                Map<String, HomeHandler.Home> ownerHomes = HomeHandler.playerHomes.get(info.ownerUUID);
                if (ownerHomes == null || !ownerHomes.containsKey(info.homeName)) {
                    continue;
                }
                HomeHandler.Home home = ownerHomes.get(info.homeName);

                menu.append(Component.literal("- " + info.homeName + "，拥有者 " + info.ownerName + "\n")
                        .withStyle(ChatFormatting.WHITE));

                String position = String.format("%s (x=%.2f, y=%.2f, z=%.2f)",
                        home.position.dimension, home.position.x, home.position.y, home.position.z);
                menu.append(Component.literal("  " + position + "\n")
                        .withStyle(ChatFormatting.GRAY));

                menu.append(Component.literal("  "))
                        .append(createI18nButton("menu.tpatools.home.button.teleport",
                                "menu.tpatools.home.hover.teleport",
                                "/home otherhome " + info.ownerName + ":" + info.homeName,
                                ChatFormatting.GREEN,
                                true))
                        .append(Component.literal("\n\n"));
            }

            if (totalPages > 1) {
                menu.append(Component.literal("=============================\n")
                        .withStyle(ChatFormatting.GOLD));

                MutableComponent pagination = Component.literal(" ");
                pagination.append(createI18nButton("menu.tpatools.home.button.previouspage",
                                "menu.tpatools.home.hover.previouspage",
                                "/home otherlist page " + Math.max(0, page - 1),
                                ChatFormatting.GRAY,
                                true))
                        .append(Component.literal(" - " + (page + 1) + "/" + totalPages + " - "))
                        .append(createI18nButton("menu.tpatools.home.button.nextpage",
                                "menu.tpatools.home.hover.nextpage",
                                "/home otherlist page " + Math.min(totalPages - 1, page + 1),
                                ChatFormatting.GRAY,
                                true));

                menu.append(pagination).append(Component.literal("\n"));
            } else {
                menu.append(Component.literal("=============================\n")
                        .withStyle(ChatFormatting.GOLD));
            }

            player.sendSystemMessage(menu);
        }

        public static void showSharedInList(ServerPlayer player, int page) {
            List<HomeHandler.Home> sharedHomes = new ArrayList<>();
            List<String> ownerNames = new ArrayList<>();

            for (Map.Entry<UUID, Map<String, HomeHandler.Home>> entry : HomeHandler.playerHomes.entrySet()) {
                UUID ownerUUID = entry.getKey();
                if (!ownerUUID.equals(player.getUUID())) {
                    for (Map.Entry<String, HomeHandler.Home> homeEntry : entry.getValue().entrySet()) {
                        if (homeEntry.getValue().sharedPlayers.contains(player.getUUID())) {
                            sharedHomes.add(homeEntry.getValue());
                            String ownerName = player.getServer().getProfileCache().get(ownerUUID)
                                    .map(profile -> profile.getName()).orElse("Unknown");
                            ownerNames.add(ownerName + ":" + homeEntry.getKey());
                        }
                    }
                }
            }

            if (sharedHomes.isEmpty()) {
                player.sendSystemMessage(ModUtils.translateWithFallback(
                        "command.tpatools.sharelist.in_empty", "No homes are shared with you."));
                return;
            }

            int pageSize = 5;
            int totalPages = (int) Math.ceil((double) sharedHomes.size() / pageSize);
            page = Math.max(0, Math.min(page, totalPages - 1));

            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - Home/sharelist/in ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.home.title.sharelist_in", "        - Homes Shared With You -\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));

            // 显示当前页的分享家
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, sharedHomes.size());

            for (int i = startIndex; i < endIndex; i++) {
                HomeHandler.Home home = sharedHomes.get(i);
                String ownerHomeName = ownerNames.get(i);

                String[] parts = ownerHomeName.split(":", 2);
                String ownerName = parts[0];
                String homeName = parts[1];

                menu.append(Component.literal("- " + homeName + " ,拥有者:" + ownerName + "\n")
                        .withStyle(ChatFormatting.WHITE));

                String position = String.format("%s (x=%.2f, y=%.2f, z=%.2f)",
                        home.position.dimension, home.position.x, home.position.y, home.position.z);
                menu.append(Component.literal("  " + position + "\n")
                        .withStyle(ChatFormatting.GRAY));

                // 传送按钮
                menu.append(Component.literal("  "))
                        .append(createI18nButton("menu.tpatools.home.button.teleport",
                                "menu.tpatools.home.hover.teleport",
                                "/home otherhome " + ownerHomeName,
                                ChatFormatting.GREEN,
                                true))
                        .append(Component.literal("\n\n"));
            }

            // 分页系统
            if (totalPages > 1) {
                menu.append(Component.literal("=============================\n")
                        .withStyle(ChatFormatting.GOLD));

                MutableComponent pagination = Component.literal(" ");
                pagination.append(createI18nButton("menu.tpatools.home.button.previouspage",
                                "menu.tpatools.home.hover.previouspage",
                                "/home sharelist in page " + Math.max(0, page - 1),
                                ChatFormatting.GRAY,
                                true))
                        .append(Component.literal(" - " + (page + 1) + "/" + totalPages + " - "))
                        .append(createI18nButton("menu.tpatools.home.button.nextpage",
                                "menu.tpatools.home.hover.nextpage",
                                "/home sharelist in page " + Math.min(totalPages - 1, page + 1),
                                ChatFormatting.GRAY,
                                true));

                menu.append(pagination).append(Component.literal("\n"));
            } else {
                menu.append(Component.literal("=============================\n")
                        .withStyle(ChatFormatting.GOLD));
            }

            player.sendSystemMessage(menu);
        }
    }

    public static class ConfigMenus {

        public static void showSettingsMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - setting ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.title.main", "TPATools Control Panel\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n"));

            // 按钮

            if (player.hasPermissions(2)) {
                menu.append(createI18nButton("menu.tpatools.config.button.configmenu",
                                "menu.tpatools.config.hover.configmenu",
                                "/tpatools configs",
                                ChatFormatting.AQUA,
                                true))
                        .append(Component.literal(" "))
                        .append(createI18nButton("menu.tpatools.config.button.debugmode",
                                "menu.tpatools.config.hover.debugmode",
                                "/tpatools debug",
                                ChatFormatting.YELLOW,
                                true))
                        .append(Component.literal(" "));
                }

                    menu.append(createI18nButton("menu.tpatools.config.button.about",
                            "menu.tpatools.config.hover.about",
                            "/tpatools about",
                            ChatFormatting.LIGHT_PURPLE,
                            true))
                    .append(Component.literal("\n"));
            // 分割线
            menu.append(Component.literal("=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showConfigsMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - setting ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.branch.setlanguage", "- Set Language (setlanguage)\n")
                            .withStyle(ChatFormatting.WHITE));

            ChatFormatting langColor = "zh_cn".equals(ModConfigs.DEFAULT_LANGUAGE.get()) ?
                    ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setlanguage_1",
                            "menu.tpatools.config.hover.setlanguage",
                            "/tpatools configs setlanguage zh_cn",
                            langColor,
                            true))
                    .append(Component.literal(" "));

            langColor = "en_us".equals(ModConfigs.DEFAULT_LANGUAGE.get()) ?
                    ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setlanguage_2",
                            "menu.tpatools.config.hover.setlanguage",
                            "/tpatools configs setlanguage en_us",
                            langColor,
                            true))
                    .append(Component.literal(" "));

            langColor = "fr_fr".equals(ModConfigs.DEFAULT_LANGUAGE.get()) ?
                    ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setlanguage_3",
                            "menu.tpatools.config.hover.setlanguage",
                            "/tpatools configs setlanguage fr_fr",
                            langColor,
                            true))
                    .append(Component.literal(" "));

            langColor = "es_es".equals(ModConfigs.DEFAULT_LANGUAGE.get()) ?
                    ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setlanguage_4",
                            "menu.tpatools.config.hover.setlanguage",
                            "/tpatools configs setlanguage es_es",
                            langColor,
                            true))
                    .append(Component.literal(" "));

            langColor = "pt_br".equals(ModConfigs.DEFAULT_LANGUAGE.get()) ?
                    ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setlanguage_5",
                            "menu.tpatools.config.hover.setlanguage",
                            "/tpatools configs setlanguage pt_br",
                            langColor,
                            true))
                    .append(Component.literal(" "));

            langColor = "zh_cn_cute".equals(ModConfigs.DEFAULT_LANGUAGE.get()) ?
                    ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setlanguage_6",
                            "menu.tpatools.config.hover.setlanguage",
                            "/tpatools configs setlanguage zh_cn_cute",
                            langColor,
                            true))
                    .append(Component.literal("\n"));

            menu.append(ModUtils.translateWithFallback("menu.tpatools.config.branch.setmaxhome", "- Set Maximum Number of Homes (setmaxhome)\n")
                    .withStyle(ChatFormatting.WHITE));

            int maxHomes = ModConfigs.MAX_HOMES.get();
            ChatFormatting color2 = maxHomes == 2 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setmaxhome_2",
                            "menu.tpatools.config.hover.setmaxhome",
                            "/tpatools configs setmaxhome 2",
                            color2,
                            true))
                    .append(Component.literal(" "));

            color2 = maxHomes == 3 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setmaxhome_3",
                            "menu.tpatools.config.hover.setmaxhome",
                            "/tpatools configs setmaxhome 3",
                            color2,
                            true))
                    .append(Component.literal(" "));

            color2 = maxHomes == 5 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setmaxhome_5",
                            "menu.tpatools.config.hover.setmaxhome",
                            "/tpatools configs setmaxhome 5",
                            color2,
                            true))
                    .append(Component.literal(" "));

            menu.append(createI18nButton("menu.tpatools.config.button.setmaxhome",
                            "menu.tpatools.config.hover.setmaxhome",
                            "/tpatools configs setmaxhome ",
                            ChatFormatting.GRAY,
                            false))
                    .append(Component.literal("\n"));

            menu.append(ModUtils.translateWithFallback("menu.tpatools.config.branch.needop", "- Configure Whether Specific Commands Require OP (needop)\n")
                    .withStyle(ChatFormatting.WHITE));

            ChatFormatting tpaColor = ModConfigs.commandPermissions.getOrDefault("tpa", false) ?
                    ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.needop_tpa",
                            "menu.tpatools.config.hover.needop_tpa",
                            "/tpatools configs needop tpa " + (!ModConfigs.commandPermissions.getOrDefault("tpa", false)),
                            tpaColor,
                            true))
                    .append(Component.literal(" "));

            ChatFormatting homeColor = ModConfigs.commandPermissions.getOrDefault("home", false) ?
                    ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.needop_home",
                            "menu.tpatools.config.hover.needop_home",
                            "/tpatools configs needop home " + (!ModConfigs.commandPermissions.getOrDefault("home", false)),
                            homeColor,
                            true))
                    .append(Component.literal(" "));

            ChatFormatting graveColor = ModConfigs.commandPermissions.getOrDefault("grave", false) ?
                    ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.needop_grave",
                            "menu.tpatools.config.hover.needop_grave",
                            "/tpatools configs needop grave " + (!ModConfigs.commandPermissions.getOrDefault("grave", false)),
                            graveColor,
                            true))
                    .append(Component.literal(" "));

            ChatFormatting backColor = ModConfigs.commandPermissions.getOrDefault("back", false) ?
                    ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.needop_back",
                            "menu.tpatools.config.hover.needop_back",
                            "/tpatools configs needop back " + (!ModConfigs.commandPermissions.getOrDefault("back", false)),
                            backColor,
                            true))
                    .append(Component.literal(" "));

            ChatFormatting warpColor = ModConfigs.commandPermissions.getOrDefault("warp", false) ?
                    ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.needop_warp",
                            "menu.tpatools.config.hover.needop_warp",
                            "/tpatools configs needop warp " + (!ModConfigs.commandPermissions.getOrDefault("warp", false)),
                            warpColor,
                            true))
                    .append(Component.literal(" "));

            ChatFormatting rtpColor = ModConfigs.commandPermissions.getOrDefault("rtp", false) ?
                    ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.needop_rtp",
                            "menu.tpatools.config.hover.needop_rtp",
                            "/tpatools configs needop rtp " + (!ModConfigs.commandPermissions.getOrDefault("rtp", false)),
                            rtpColor,
                            true))
                    .append(Component.literal("\n"));

            menu.append(ModUtils.translateWithFallback("menu.tpatools.config.branch.tpawaittime", "- TPA Timeout Duration (tpawaittime)\n")
                    .withStyle(ChatFormatting.WHITE));

            int waitTime = (ModConfigs.TIMEOUT_TICKS.get() / 20);
            ChatFormatting waitColor = waitTime == 30 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.tpawaittime_30",
                            "menu.tpatools.config.hover.tpawaittime",
                            "/tpatools configs tpawaittime 30",
                            waitColor,
                            true))
                    .append(Component.literal(" "));

            waitColor = waitTime == 60 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.tpawaittime_60",
                            "menu.tpatools.config.hover.tpawaittime",
                            "/tpatools configs tpawaittime 60",
                            waitColor,
                            true))
                    .append(Component.literal(" "));

            menu.append(createI18nButton("menu.tpatools.config.button.tpawaittime",
                            "menu.tpatools.config.hover.tpawaittime",
                            "/tpatools configs tpawaittime ",
                            ChatFormatting.GRAY,
                            false))
                    .append(Component.literal("\n"));

            menu.append(ModUtils.translateWithFallback("menu.tpatools.config.branch.tpacdtime", "- TPA Cooldown Duration (tpacdtime)\n")
                    .withStyle(ChatFormatting.WHITE));

            int cdTime = (int) (ModConfigs.COOLDOWN_TIME.get() / 1000);
            ChatFormatting cdColor = cdTime == 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.tpacdtime_0",
                            "menu.tpatools.config.hover.tpacdtime",
                            "/tpatools configs tpacdtime 0",
                            cdColor,
                            true))
                    .append(Component.literal(" "));

            cdColor = cdTime == 10 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.tpacdtime_10",
                            "menu.tpatools.config.hover.tpacdtime",
                            "/tpatools configs tpacdtime 10",
                            cdColor,
                            true))
                    .append(Component.literal(" "));

            cdColor = cdTime == 30 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.tpacdtime_30",
                            "menu.tpatools.config.hover.tpacdtime",
                            "/tpatools configs tpacdtime 30",
                            cdColor,
                            true))
                    .append(Component.literal(" "));

            menu.append(createI18nButton("menu.tpatools.config.button.tpacdtime",
                            "menu.tpatools.config.hover.tpacdtime",
                            "/tpatools configs tpacdtime ",
                            ChatFormatting.GRAY,
                            false))
                    .append(Component.literal("\n"));

            menu.append(ModUtils.translateWithFallback("menu.tpatools.config.branch.homeinvitecdtime", "- Home Invite Cooldown Duration (homeinvitecdtime)\n")
                    .withStyle(ChatFormatting.WHITE));

            int homeInviteCDTime = ModConfigs.HOME_INVITE_COOLDOWN.get();
            ChatFormatting homeCDColor = homeInviteCDTime == 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.homeinvitecdtime_0",
                            "menu.tpatools.config.hover.homeinvitecdtime",
                            "/tpatools configs homeinvitecdtime 0",
                            homeCDColor,
                            true))
                    .append(Component.literal(" "));

            homeCDColor = homeInviteCDTime == 10 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.homeinvitecdtime_10",
                            "menu.tpatools.config.hover.homeinvitecdtime",
                            "/tpatools configs homeinvitecdtime 10",
                            homeCDColor,
                            true))
                    .append(Component.literal(" "));

            homeCDColor = homeInviteCDTime == 30 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.homeinvitecdtime_30",
                            "menu.tpatools.config.hover.homeinvitecdtime",
                            "/tpatools configs homeinvitecdtime 30",
                            homeCDColor,
                            true))
                    .append(Component.literal(" "));

            menu.append(createI18nButton("menu.tpatools.config.button.homeinvitecdtime",
                            "menu.tpatools.config.hover.homeinvitecdtime",
                            "/tpatools configs homeinvitecdtime ",
                            ChatFormatting.GRAY,
                            false))
                    .append(Component.literal("\n"));

            menu.append(ModUtils.translateWithFallback("menu.tpatools.config.branch.homeinviteovertime", "- Home Invite Timeout Duration (homeinviteovertime)\n")
                    .withStyle(ChatFormatting.WHITE));

            int homeInviteOverTime = ModConfigs.HOME_INVITE_TIMEOUT.get();
            ChatFormatting homeOTColor = homeInviteOverTime == 30 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.homeinviteovertime_30",
                            "menu.tpatools.config.hover.homeinviteovertime",
                            "/tpatools configs homeinviteovertime 30",
                            homeOTColor,
                            true))
                    .append(Component.literal(" "));

            homeOTColor = homeInviteOverTime == 60 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.homeinviteovertime_60",
                            "menu.tpatools.config.hover.homeinviteovertime",
                            "/tpatools configs homeinviteovertime 60",
                            homeOTColor,
                            true))
                    .append(Component.literal(" "));

            homeOTColor = homeInviteOverTime == 120 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.homeinviteovertime_120",
                            "menu.tpatools.config.hover.homeinviteovertime",
                            "/tpatools configs homeinviteovertime 120",
                            homeOTColor,
                            true))
                    .append(Component.literal(" "));

            menu.append(createI18nButton("menu.tpatools.config.button.homeinviteovertime",
                            "menu.tpatools.config.hover.homeinviteovertime",
                            "/tpatools configs homeinviteovertime ",
                            ChatFormatting.GRAY,
                            false))
                    .append(Component.literal("\n"));

            // 骑乘实体传送设置
            boolean allowRideTeleport = ModConfigs.ALLOW_TELEPORT_RIDE_ENTITY.get();
            ChatFormatting rideColor = allowRideTeleport ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.branch.allowteleportrideentity",
                            "menu.tpatools.config.hover.allowteleportrideentity",
                            "/tpatools configs allowteleportrideentity",
                            ChatFormatting.WHITE,
                            true))
                    .append(createI18nButton(allowRideTeleport ? "menu.tpatools.config.button.allowteleportrideentity_true" : "menu.tpatools.config.button.allowteleportrideentity_false",
                            "menu.tpatools.config.hover.allowteleportrideentity",
                            "/tpatools configs allowteleportrideentity " + (!allowRideTeleport),
                            rideColor,
                            true))
                    .append(Component.literal("\n"));

            // 安全传送设置
            boolean safeTeleport = ModConfigs.SAFE_TELEPORT.get();
            ChatFormatting safeColor = safeTeleport ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.branch.safeteleport",
                            "menu.tpatools.config.hover.safeteleport",
                            "/tpatools configs safeteleport",
                            ChatFormatting.WHITE,
                            true))
                    .append(createI18nButton(safeTeleport ? "menu.tpatools.config.button.safeteleport_true" : "menu.tpatools.config.button.safeteleport_false",
                            "menu.tpatools.config.hover.safeteleport",
                            "/tpatools configs safeteleport " + (!safeTeleport),
                            safeColor,
                            true))
                    .append(Component.literal("\n"));

            // 传送延迟判定
            boolean teleportNeedTime = ModConfigs.TELEPORT_NEED_TIME.get();
            ChatFormatting needTimeColor = teleportNeedTime ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.branch.teleportneedtime",
                            "menu.tpatools.config.hover.teleportneedtime",
                            "/tpatools configs teleportneedtime",
                            ChatFormatting.WHITE,
                            true))
                    .append(createI18nButton(teleportNeedTime ? "menu.tpatools.config.button.teleportneedtime_true" : "menu.tpatools.config.button.teleportneedtime_false",
                            "menu.tpatools.config.hover.teleportneedtime",
                            "/tpatools configs teleportneedtime " + (!teleportNeedTime),
                            needTimeColor,
                            true))
                    .append(Component.literal("\n"));

            int teleportDelayTime = ModConfigs.TELEPORT_DELAY_TIME.get();
            ChatFormatting delayTimeColor = 3 == teleportDelayTime ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.branch.teleportdelaytime",
                            "menu.tpatools.config.hover.teleportdelaytime",
                            "/tpatools configs teleportdelaytime",
                            ChatFormatting.WHITE,
                            true))
                    .append(createI18nButton("menu.tpatools.config.button.teleportdelaytime_3",
                            "menu.tpatools.config.hover.teleportdelaytime",
                            "/tpatools configs teleportdelaytime 3",
                            delayTimeColor,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.teleportdelaytime_5",
                            "menu.tpatools.config.hover.teleportdelaytime",
                            "/tpatools configs teleportdelaytime 5",
                            5 == teleportDelayTime ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.teleportdelaytime_custom",
                            "menu.tpatools.config.hover.teleportdelaytime",
                            "/tpatools configs teleportdelaytime ",
                            ChatFormatting.AQUA,
                            false))
                    .append(Component.literal("\n"));

            menu.append(ModUtils.translateWithFallback("menu.tpatools.config.branch.rtpscope", "- RTP Scope (rtpscope)\n")
                    .withStyle(ChatFormatting.WHITE));

            int rtpScope = ModConfigs.RTP_SCOPE.get();
            ChatFormatting rtpScopeColor = rtpScope == 10000 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.rtpscope_10000",
                            "menu.tpatools.config.hover.rtpscope",
                            "/tpatools configs rtpscope 10000",
                            rtpScopeColor,
                            true))
                    .append(Component.literal(" "));
            rtpScopeColor = rtpScope == 20000 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.rtpscope_20000",
                            "menu.tpatools.config.hover.rtpscope",
                            "/tpatools configs rtpscope 20000",
                            rtpScopeColor,
                            true))
                    .append(Component.literal(" "));
            rtpScopeColor = rtpScope == 50000 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.rtpscope_50000",
                            "menu.tpatools.config.hover.rtpscope",
                            "/tpatools configs rtpscope 50000",
                            rtpScopeColor,
                            true))
                    .append(Component.literal(" "));
            rtpScopeColor = rtpScope == 100000 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.rtpscope_100000",
                            "menu.tpatools.config.hover.rtpscope",
                            "/tpatools configs rtpscope 100000",
                            rtpScopeColor,
                            true))
                    .append(Component.literal(" "));
            menu.append(createI18nButton("menu.tpatools.config.button.rtpscope_custom",
                            "menu.tpatools.config.hover.rtpscope",
                            "/tpatools configs rtpscope ",
                            ChatFormatting.GRAY,
                            false))
                    .append(Component.literal("\n"));

            menu.append(ModUtils.translateWithFallback("menu.tpatools.config.branch.rtpcdtime", "- RTP Cooldown Time (rtpcdtime)\n")
                    .withStyle(ChatFormatting.WHITE));

            int rtpcdtime = ModConfigs.RTP_COOLDOWN_TIME.get();
            ChatFormatting rtpcdtimeColor = rtpcdtime == 30 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.rtpcdtime_30",
                            "menu.tpatools.config.hover.rtpcdtime",
                            "/tpatools configs rtpcdtime 30",
                            rtpcdtimeColor,
                            true))
                    .append(Component.literal(" "));
            rtpcdtimeColor = rtpcdtime == 60 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.rtpcdtime_60",
                            "menu.tpatools.config.hover.rtpcdtime",
                            "/tpatools configs rtpcdtime 60",
                            rtpcdtimeColor,
                            true))
                    .append(Component.literal(" "));
            rtpcdtimeColor = rtpcdtime == 120 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.rtpcdtime_120",
                            "menu.tpatools.config.hover.rtpcdtime",
                            "/tpatools configs rtpcdtime 120",
                            rtpcdtimeColor,
                            true))
                    .append(Component.literal(" "));
            menu.append(createI18nButton("menu.tpatools.config.button.rtpcdtime_custom",
                            "menu.tpatools.config.hover.rtpcdtime",
                            "/tpatools configs rtpcdtime ",
                            ChatFormatting.GRAY,
                            false))
                    .append(Component.literal("\n"));

            menu.append(ModUtils.translateWithFallback("menu.tpatools.config.branch.setmaxwarpcount", "- Max Warp Count set (setmaxwarpcount)\n")
                    .withStyle(ChatFormatting.WHITE));
            int maxwarpcount = ModConfigs.MAX_WARP_COUNT.get();
            ChatFormatting maxwarpcountColor = maxwarpcount == 1 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setmaxwarpcount_1",
                            "menu.tpatools.config.hover.setmaxwarpcount",
                            "/tpatools configs setmaxwarpcount 1",
                            maxwarpcountColor,
                            true))
                    .append(Component.literal(" "));
            maxwarpcountColor = maxwarpcount == 2 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setmaxwarpcount_2",
                            "menu.tpatools.config.hover.setmaxwarpcount",
                            "/tpatools configs setmaxwarpcount 2",
                            maxwarpcountColor,
                            true))
                    .append(Component.literal(" "));
            maxwarpcountColor = maxwarpcount == 3 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setmaxwarpcount_3",
                            "menu.tpatools.config.hover.setmaxwarpcount",
                            "/tpatools configs setmaxwarpcount 3",
                            maxwarpcountColor,
                            true))
                    .append(Component.literal(" "));
            maxwarpcountColor = maxwarpcount == 4 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setmaxwarpcount_4",
                            "menu.tpatools.config.hover.setmaxwarpcount",
                            "/tpatools configs setmaxwarpcount 4",
                            maxwarpcountColor,
                            true))
                    .append(Component.literal(" "));
            maxwarpcountColor = maxwarpcount == 5 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton("menu.tpatools.config.button.setmaxwarpcount_5",
                            "menu.tpatools.config.hover.setmaxwarpcount",
                            "/tpatools configs setmaxwarpcount 5",
                            maxwarpcountColor,
                            true))
                    .append(Component.literal(" "));
            menu.append(createI18nButton("menu.tpatools.config.button.setmaxwarpcount_custom",
                            "menu.tpatools.config.hover.setmaxwarpcount",
                            "/tpatools configs setmaxwarpcount ",
                            ChatFormatting.GRAY,
                            false))
                    .append(Component.literal("\n"));


            menu.append(ModUtils.translateWithFallback("menu.tpatools.config.branch.debug", "- Debug Mode (debug)\n")
                    .withStyle(ChatFormatting.WHITE));

            boolean debugEnabled = ModConfigs.isDebugEnabled();
            ChatFormatting debugColor = debugEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            menu.append(createI18nButton(debugEnabled ? "menu.tpatools.config.button.debugvalue.true" : "menu.tpatools.config.button.debugvalue.false",
                    "menu.tpatools.config.hover.debugvalue",
                    "/tpatools debug " + (!debugEnabled),
                    debugColor,
                    true));

            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showSetLanguageMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - setting/setlanguage ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.setlanguage", "This setting is used to modify the hot-reload language feature of the TPATools mod.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.setlanguage", "- Default value: zh_cn (currently {})\n", ModConfigs.DEFAULT_LANGUAGE.get())
                            .withStyle(ChatFormatting.GRAY));

            // 按钮
            menu.append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setlanguage_1",
                            "menu.tpatools.config.hover.setlanguage",
                            "/tpatools configs setlanguage zh_cn",
                            "zh_cn".equals(ModConfigs.DEFAULT_LANGUAGE.get()) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setlanguage_2",
                            "menu.tpatools.config.hover.setlanguage",
                            "/tpatools configs setlanguage en_us",
                            "en_us".equals(ModConfigs.DEFAULT_LANGUAGE.get()) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setlanguage_3",
                            "menu.tpatools.config.hover.setlanguage",
                            "/tpatools configs setlanguage fr_fr",
                            "fr_fr".equals(ModConfigs.DEFAULT_LANGUAGE.get()) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setlanguage_4",
                            "menu.tpatools.config.hover.setlanguage",
                            "/tpatools configs setlanguage pt_br",
                            "pt_br".equals(ModConfigs.DEFAULT_LANGUAGE.get()) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setlanguage_5",
                            "menu.tpatools.config.hover.setlanguage",
                            "/tpatools configs setlanguage es_es",
                            "es_es".equals(ModConfigs.DEFAULT_LANGUAGE.get()) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setlanguage_6",
                            "menu.tpatools.config.hover.setlanguage",
                            "/tpatools configs setlanguage zh_cn_cute",
                            "zh_cn_cute".equals(ModConfigs.DEFAULT_LANGUAGE.get()) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true));

            // 分割线
            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showSetMaxHomeMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - setting/setmaxhome ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.setmaxhome", "This setting controls the maximum number of homes allowed for all players on the server.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.setmaxhome", "- Default: 2 (currently: {})\n", ModConfigs.MAX_HOMES.get())
                            .withStyle(ChatFormatting.GRAY));

            // 按钮
            menu.append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setmaxhome_2",
                            "menu.tpatools.config.hover.setmaxhome",
                            "/tpatools configs setmaxhome 2",
                            2 == ModConfigs.MAX_HOMES.get() ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setmaxhome_3",
                            "menu.tpatools.config.hover.setmaxhome",
                            "/tpatools configs setmaxhome 3",
                            3 == ModConfigs.MAX_HOMES.get() ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setmaxhome_5",
                            "menu.tpatools.config.hover.setmaxhome",
                            "/tpatools configs setmaxhome 5",
                            5 == ModConfigs.MAX_HOMES.get() ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setmaxhome",
                            "menu.tpatools.config.hover.setmaxhome",
                            "/tpatools configs setmaxhome ",
                            ChatFormatting.GRAY,
                            false));

            // 分割线
            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showNeedOpMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - setting/needop ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.needop", "Used to specify whether certain commands require OP permission.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.needop", "- Default: tpa:false home:false grave:false back:false\n (currently set to tpa:{}, home:{}, grave:{}, back:{})\n",
                                    ModConfigs.commandPermissions.getOrDefault("tpa", false),
                                    ModConfigs.commandPermissions.getOrDefault("home", false),
                                    ModConfigs.commandPermissions.getOrDefault("grave", false),
                                    ModConfigs.commandPermissions.getOrDefault("back", false))
                            .withStyle(ChatFormatting.GRAY));

            // 按钮
            menu.append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.needop_tpa",
                            "menu.tpatools.config.hover.needop_tpa",
                            "/tpatools configs needop tpa " + (!ModConfigs.commandPermissions.getOrDefault("tpa", false)),
                            ModConfigs.commandPermissions.getOrDefault("tpa", false) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.needop_home",
                            "menu.tpatools.config.hover.needop_home",
                            "/tpatools configs needop home " + (!ModConfigs.commandPermissions.getOrDefault("home", false)),
                            ModConfigs.commandPermissions.getOrDefault("home", false) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.needop_grave",
                            "menu.tpatools.config.hover.needop_grave",
                            "/tpatools configs needop grave " + (!ModConfigs.commandPermissions.getOrDefault("grave", false)),
                            ModConfigs.commandPermissions.getOrDefault("grave", false) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.needop_back",
                            "menu.tpatools.config.hover.needop_back",
                            "/tpatools configs needop back " + (!ModConfigs.commandPermissions.getOrDefault("back", false)),
                            ModConfigs.commandPermissions.getOrDefault("back", false) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.needop_warp",
                            "menu.tpatools.config.hover.needop_warp",
                            "/tpatools configs needop warp " + (!ModConfigs.commandPermissions.getOrDefault("warp", false)),
                            ModConfigs.commandPermissions.getOrDefault("warp", false) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.needop_rtp",
                            "menu.tpatools.config.hover.needop_rtp",
                            "/tpatools configs needop rtp " + (!ModConfigs.commandPermissions.getOrDefault("rtp", false)),
                            ModConfigs.commandPermissions.getOrDefault("rtp", false) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true));

            // 分割线
            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showTPAWaitTimeMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - setting/tpawaittime ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.tpawaittime", "Sets the timeout duration for TPA requests.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.tpawaittime", "- Default: 30 (unit: seconds) (currently set to {} seconds)\n", (ModConfigs.TIMEOUT_TICKS.get() / 20))
                            .withStyle(ChatFormatting.GRAY));

            // 按钮
            menu.append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.tpawaittime_30",
                            "menu.tpatools.config.hover.tpawaittime",
                            "/tpatools configs tpawaittime 30",
                            30 == (ModConfigs.TIMEOUT_TICKS.get() / 20) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.tpawaittime_60",
                            "menu.tpatools.config.hover.tpawaittime",
                            "/tpatools configs tpawaittime 60",
                            60 == (ModConfigs.TIMEOUT_TICKS.get() / 20) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.tpawaittime",
                            "menu.tpatools.config.hover.tpawaittime",
                            "/tpatools configs tpawaittime ",
                            ChatFormatting.GRAY,
                            false));

            // 分割线
            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showTPACDTimeMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - setting/tpacdtime ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.tpacdtime", "Sets the cooldown duration between TPA requests.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.tpacdtime", "- Default: 0 (unit: seconds) (currently set to {} seconds)\n", (ModConfigs.COOLDOWN_TIME.get() / 1000)))
                    .withStyle(ChatFormatting.GRAY);

            // 按钮
            menu.append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.tpacdtime_0",
                            "menu.tpatools.config.hover.tpacdtime",
                            "/tpatools configs tpacdtime 0",
                            0 == (ModConfigs.COOLDOWN_TIME.get() / 1000) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.tpacdtime_10",
                            "menu.tpatools.config.hover.tpacdtime",
                            "/tpatools configs tpacdtime 10",
                            10 == (ModConfigs.COOLDOWN_TIME.get() / 1000) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.tpacdtime_30",
                            "menu.tpatools.config.hover.tpacdtime",
                            "/tpatools configs tpacdtime 30",
                            30 == (ModConfigs.COOLDOWN_TIME.get() / 1000) ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.tpacdtime",
                            "menu.tpatools.config.hover.tpacdtime",
                            "/tpatools configs tpacdtime ",
                            ChatFormatting.GRAY,
                            false));

            // 分割线
            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showDebugMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - setting/debug ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.debug", "Used to enable debug mode.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.debug_2", "Important note: This mode only toggles logging for this mod on the server. If you are an administrator but cannot view logs, please enable this cautiously.\n")
                            .withStyle(ChatFormatting.RED))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.debug", "- Default: false (currently set to {})\n", ModConfigs.isDebugEnabled()))
                    .withStyle(ChatFormatting.GRAY);

            // 按钮
            boolean debugEnabled = ModConfigs.isDebugEnabled();
            menu.append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.debugvalue.false",
                            "menu.tpatools.config.hover.debugvalue.false",
                            "/tpatools debug false",
                            !debugEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.debugvalue.true",
                            "menu.tpatools.config.hover.debugvalue.true",
                            "/tpatools debug true",
                            debugEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true));

            // 分割线
            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showAllowTeleportRideEntityMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");
            menu.append(Component.literal("====== TPATools - setting/allowteleportrideentity ======\n")
                    .withStyle(ChatFormatting.GOLD));
            // 骑乘实体传送设置
            boolean allowRideTeleport = ModConfigs.ALLOW_TELEPORT_RIDE_ENTITY.get();
            ChatFormatting rideColor = allowRideTeleport ? ChatFormatting.GREEN : ChatFormatting.RED;
            menu.append(ModUtils.translateWithFallback("menu.tpatools.config.tips.allowteleportrideentity",
                                    "This setting controls whether to teleport ridden entities with players when using /tpa.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.allowteleportrideentity",
                                    "- Default: false (currently set to %s)\n", allowRideTeleport)
                            .withStyle(ChatFormatting.GRAY))
                    .append(createI18nButton(allowRideTeleport ? "menu.tpatools.config.button.allowteleportrideentity_true" : "menu.tpatools.config.button.allowteleportrideentity_false",
                            "menu.tpatools.config.hover.allowteleportrideentity",
                            "/tpatools configs allowteleportrideentity " + (!allowRideTeleport),
                            rideColor,
                            true));

            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showSafeTeleportMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");
            menu.append(Component.literal("====== TPATools - setting/safeteleport ======\n")
                    .withStyle(ChatFormatting.GOLD));
            boolean safeTeleport = ModConfigs.SAFE_TELEPORT.get();
            ChatFormatting safeColor = safeTeleport ? ChatFormatting.GREEN : ChatFormatting.RED;
            menu.append(ModUtils.translateWithFallback("menu.tpatools.config.tips.safeteleport",
                                    "When enabled, if the teleport destination is blocked, the system will search for a safe nearby position.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.safeteleport",
                                    "- Default: true (currently set to %s)\n", safeTeleport)
                            .withStyle(ChatFormatting.GRAY))
                    .append(createI18nButton(safeTeleport ? "menu.tpatools.config.button.safeteleport_true" : "menu.tpatools.config.button.safeteleport_false",
                            "menu.tpatools.config.hover.safeteleport",
                            "/tpatools configs safeteleport " + (!safeTeleport),
                            safeColor,
                            true));

            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showTeleportNeedTimeMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            boolean teleportNeedTime = ModConfigs.TELEPORT_NEED_TIME.get();
            ChatFormatting color = teleportNeedTime ? ChatFormatting.GREEN : ChatFormatting.RED;
            menu.append(Component.literal("====== TPATools - setting/teleportneedtime ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.teleportneedtime",
                                    "When enabled, players must stand still for a few seconds before the teleport happens.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.teleportneedtime",
                                    "- Default: false (currently set to %s)", teleportNeedTime)
                            .withStyle(ChatFormatting.GRAY))
                    .append(createI18nButton(teleportNeedTime ? "menu.tpatools.config.button.teleportneedtime_true" : "menu.tpatools.config.button.teleportneedtime_false",
                            "menu.tpatools.config.hover.teleportneedtime",
                            "/tpatools configs teleportneedtime " + (!teleportNeedTime),
                            color,
                            true));

            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showTeleportDelayTimeMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            int teleportDelayTime = ModConfigs.TELEPORT_DELAY_TIME.get();
            menu.append(Component.literal("====== TPATools - setting/teleportdelaytime ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.teleportdelaytime",
                                    "This setting controls how many seconds players must stand still before the teleport happens.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.teleportdelaytime",
                                    "- Default: 3 (unit: seconds) (currently set to %d seconds)", teleportDelayTime)
                            .withStyle(ChatFormatting.GRAY));

            menu.append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.teleportdelaytime_3",
                            "menu.tpatools.config.hover.teleportdelaytime",
                            "/tpatools configs teleportdelaytime 3",
                            3 == teleportDelayTime ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.teleportdelaytime_5",
                            "menu.tpatools.config.hover.teleportdelaytime",
                            "/tpatools configs teleportdelaytime 5",
                            5 == teleportDelayTime ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.teleportdelaytime_custom",
                            "menu.tpatools.config.hover.teleportdelaytime",
                            "/tpatools configs teleportdelaytime ",
                            ChatFormatting.AQUA,
                            false));

            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showAboutMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");
            menu.append(Component.literal("====== TPATools - about ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.about.main", "Thanx for using TPATools. Your translation maybe not complete.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.about.url.mcmod", "[MCMod Wiki Page]")
                            .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.mcmod.cn/class/22216.html"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ModUtils.translateWithFallback("menu.tpatools.about.hover.url.mcmod", "Click to open the MCMod Wiki Page.Only for Chinese players.")))
                            )
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" "))
                    .append(ModUtils.translateWithFallback("menu.tpatools.about.url.modrinth", "[Modrinth Page]")
                            .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://modrinth.com/mod/tpatools"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ModUtils.translateWithFallback("menu.tpatools.about.hover.url.modrinth", "Click to open the Modrinth Page.")))
                            )
                            .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" "))
                    .append(ModUtils.translateWithFallback("menu.tpatools.about.url.github", "[GitHub issue Page]")
                            .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/kicobicn/TPATools/issues"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ModUtils.translateWithFallback("menu.tpatools.about.hover.url.github", "Click to open the GitHub issue Page.")))
                            )
                            .withStyle(ChatFormatting.DARK_GRAY)
                    );

            menu.append(Component.literal("\n===================")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showHomeInviteCDTimeMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - setting/homeinvitecdtime ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.homeinvitecdtime", "Sets the cooldown duration between home invite requests.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.homeinvitecdtime", "- Default: 0 (unit: seconds) (currently set to {} seconds)\n", ModConfigs.HOME_INVITE_COOLDOWN.get())
                            .withStyle(ChatFormatting.GRAY));

            // 按钮
            int homeInviteCDTime = ModConfigs.HOME_INVITE_COOLDOWN.get();
            menu.append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.homeinvitecdtime_0",
                            "menu.tpatools.config.hover.homeinvitecdtime",
                            "/tpatools configs homeinvitecdtime 0",
                            0 == homeInviteCDTime ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.homeinvitecdtime_10",
                            "menu.tpatools.config.hover.homeinvitecdtime",
                            "/tpatools configs homeinvitecdtime 10",
                            10 == homeInviteCDTime ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.homeinvitecdtime_30",
                            "menu.tpatools.config.hover.homeinvitecdtime",
                            "/tpatools configs homeinvitecdtime 30",
                            30 == homeInviteCDTime ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.homeinvitecdtime",
                            "menu.tpatools.config.hover.homeinvitecdtime",
                            "/tpatools configs homeinvitecdtime ",
                            ChatFormatting.GRAY,
                            false));

            // 分割线
            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showHomeInviteOverTimeMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - setting/homeinviteovertime ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.homeinviteovertime", "Sets the timeout duration for home invite requests.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.homeinviteovertime", "- Default: 30 (unit: seconds) (currently set to {} seconds)\n", ModConfigs.HOME_INVITE_TIMEOUT.get())
                            .withStyle(ChatFormatting.GRAY));

            // 按钮
            int homeInviteOverTime = ModConfigs.HOME_INVITE_TIMEOUT.get();
            menu.append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.homeinviteovertime_30",
                            "menu.tpatools.config.hover.homeinviteovertime",
                            "/tpatools configs homeinviteovertime 30",
                            30 == homeInviteOverTime ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.homeinviteovertime_60",
                            "menu.tpatools.config.hover.homeinviteovertime",
                            "/tpatools configs homeinviteovertime 60",
                            60 == homeInviteOverTime ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.homeinviteovertime_120",
                            "menu.tpatools.config.hover.homeinviteovertime",
                            "/tpatools configs homeinviteovertime 120",
                            120 == homeInviteOverTime ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.homeinviteovertime",
                            "menu.tpatools.config.hover.homeinviteovertime",
                            "/tpatools configs homeinviteovertime ",
                            ChatFormatting.GRAY,
                            false));

            // 分割线
            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showRTPScopeMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - setting/rtpscope ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.rtpscope", "Sets the radius for random teleportation.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.rtpscope", "- Default: 100000 (unit: blocks) (currently set to %d blocks)\n", ModConfigs.RTP_SCOPE.get())
                            .withStyle(ChatFormatting.GRAY));

            // 按钮
            int rtpScope = ModConfigs.RTP_SCOPE.get();
            menu.append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.rtpscope_10000",
                            "menu.tpatools.config.hover.rtpscope",
                            "/tpatools configs rtpscope 10000",
                            10000 == rtpScope ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.rtpscope_20000",
                            "menu.tpatools.config.hover.rtpscope",
                            "/tpatools configs rtpscope 20000",
                            20000 == rtpScope ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.rtpscope_50000",
                            "menu.tpatools.config.hover.rtpscope",
                            "/tpatools configs rtpscope 50000",
                            50000 == rtpScope ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.rtpscope_100000",
                            "menu.tpatools.config.hover.rtpscope",
                            "/tpatools configs rtpscope 100000",
                            100000 == rtpScope ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.rtpscope_custom",
                            "menu.tpatools.config.hover.rtpscope",
                            "/tpatools configs rtpscope ",
                            ChatFormatting.GRAY,
                            false));

            // 分割线
            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showRTPCooldownTimeMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - setting/rtpcooldown ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.rtpcdtime", "Sets the cooldown time for random teleportation.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.rtpcdtime", "- Default: 30 (unit: seconds) (currently set to {} seconds)\n", ModConfigs.RTP_COOLDOWN_TIME.get())
                            .withStyle(ChatFormatting.GRAY));

            // 按钮
            int rtpcoolDown = ModConfigs.RTP_COOLDOWN_TIME.get();
            menu.append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.rtpcdtime_30",
                            "menu.tpatools.config.hover.rtpcdtime",
                            "/tpatools configs rtpcdtime 30",
                            30 == rtpcoolDown ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.rtpcdtime_60",
                            "menu.tpatools.config.hover.rtpcdtime",
                            "/tpatools configs rtpcdtime 60",
                            60 == rtpcoolDown ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.rtpcdtime_120",
                            "menu.tpatools.config.hover.rtpcdtime",
                            "/tpatools configs rtpcdtime 120",
                            120 == rtpcoolDown ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.rtpcdtime_custom",
                            "menu.tpatools.config.hover.rtpcdtime",
                            "/tpatools configs rtpcdtime ",
                            ChatFormatting.GRAY,
                            false));

            // 分割线
            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }

        public static void showMaxWarpCountMenu(ServerPlayer player) {
            MutableComponent menu = Component.literal("");

            // 标题
            menu.append(Component.literal("====== TPATools - setting/maxwarpcount ======\n")
                            .withStyle(ChatFormatting.GOLD))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.tips.setmaxwarpcount", "Sets the maximum number of warps.\n")
                            .withStyle(ChatFormatting.WHITE))
                    .append(ModUtils.translateWithFallback("menu.tpatools.config.default.setmaxwarpcount", "- Default: 3 (currently set to {} warps)\n", ModConfigs.MAX_WARP_COUNT.get())
                            .withStyle(ChatFormatting.GRAY));

            // 按钮
            int maxWarpCount = ModConfigs.MAX_WARP_COUNT.get();
            menu.append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setmaxwarpcount_1",
                            "menu.tpatools.config.hover.setmaxwarpcount",
                            "/tpatools configs setmaxwarpcount 1",
                            1 == maxWarpCount ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setmaxwarpcount_2",
                            "menu.tpatools.config.hover.setmaxwarpcount",
                            "/tpatools configs setmaxwarpcount 2",
                            2 == maxWarpCount ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setmaxwarpcount_3",
                            "menu.tpatools.config.hover.setmaxwarpcount",
                            "/tpatools configs setmaxwarpcount 3",
                            3 == maxWarpCount ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setmaxwarpcount_4",
                            "menu.tpatools.config.hover.setmaxwarpcount",
                            "/tpatools configs setmaxwarpcount 4",
                            4 == maxWarpCount ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setmaxwarpcount_5",
                            "menu.tpatools.config.hover.setmaxwarpcount",
                            "/tpatools configs setmaxwarpcount 5",
                            5 == maxWarpCount ? ChatFormatting.GREEN : ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.config.button.setmaxwarpcount_custom",
                            "menu.tpatools.config.hover.setmaxwarpcount",
                            "/tpatools configs setmaxwarpcount ",
                            ChatFormatting.GRAY,
                            false));

            // 分割线
            menu.append(Component.literal("\n=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            player.sendSystemMessage(menu);
        }
    }

    public static void showWarpMenu(ServerPlayer player) {
        MutableComponent menu = Component.literal("");
        // 标题
        menu.append(Component.literal("====== TPATools - warp ======\n")
                        .withStyle(ChatFormatting.GOLD))
                .append(ModUtils.translateWithFallback("menu.tpatools.warp.tip", "Warp point menu.\n")
                        .withStyle(ChatFormatting.WHITE));

        // 地标列表
        menu.append(Component.literal(" "))
                .append(createI18nButton("menu.tpatools.warp.button.warp_list",
                        "menu.tpatools.warp.hover.warp_list",
                        "/warp list",
                        ChatFormatting.GREEN,
                        true))
                .append(Component.literal(" "));
        if (player.hasPermissions(2)) {
            menu.append(Component.literal(" "))
                    .append(createI18nButton("menu.tpatools.warp.button.setwarp",
                            "menu.tpatools.warp.hover.setwarp",
                            "/warp set ",
                            ChatFormatting.GREEN,
                            false))
                    .append(Component.literal(" "));
        }

        // 分割线
        menu.append(Component.literal("\n=============================\n")
                        .withStyle(ChatFormatting.GOLD));

        player.sendSystemMessage(menu);
    }

    public static void showWarpList(ServerPlayer player, int page) {
        
        if (WarpHandler.warpPoints.isEmpty()) {
            player.sendSystemMessage(ModUtils.translateWithFallback(
                    "command.tpatools.warp.no_warps", "No warp points available."));
            return;
        }

        List<String> warpNames = new ArrayList<>(WarpHandler.warpPoints.keySet());
        int pageSize = 5;
        int totalPages = (int) Math.ceil((double) warpNames.size() / pageSize);
        page = Math.max(0, Math.min(page, totalPages - 1));

        MutableComponent menu = Component.literal("");

        // 标题
        menu.append(Component.literal("====== TPATools - Warp/list ======\n")
                        .withStyle(ChatFormatting.GOLD))
                .append(ModUtils.translateWithFallback("menu.tpatools.warp.title.warp_list", "        - Available Warp Points -\n")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"));

        // 显示当前页的warp点
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, warpNames.size());

        for (int i = startIndex; i < endIndex; i++) {
            String warpName = warpNames.get(i);
            WarpHandler.WarpPoint warp = WarpHandler.warpPoints.get(warpName);

            menu.append(Component.literal("- " + warpName + "\n")
                    .withStyle(ChatFormatting.WHITE));

            String position = String.format("%s (x=%.2f, y=%.2f, z=%.2f)",
                    warp.dimension, warp.x, warp.y, warp.z);
            menu.append(Component.literal("  " + position + "\n")
                    .withStyle(ChatFormatting.GRAY));

            // 按钮行
            MutableComponent buttons = Component.literal("  ");
            buttons.append(createI18nButton("menu.tpatools.warp.button.teleport",
                            "menu.tpatools.warp.hover.teleport",
                            "/warp tp " + warpName,
                            ChatFormatting.GREEN,
                            true))
                    .append(Component.literal(" "));

            if (player.hasPermissions(2)) { // OP权限
                buttons.append(createI18nButton("menu.tpatools.warp.button.remove",
                                "menu.tpatools.warp.hover.remove",
                                "/warp remove " + warpName,
                                ChatFormatting.RED,
                                true))
                        .append(Component.literal(" "));
            }

            menu.append(buttons).append(Component.literal("\n"));
        }

        // 分页系统
        if (totalPages > 1) {
            menu.append(Component.literal("=============================\n")
                    .withStyle(ChatFormatting.GOLD));

            MutableComponent pagination = Component.literal(" ");
            pagination.append(createI18nButton("menu.tpatools.warp.button.previouspage",
                            "menu.tpatools.warp.hover.previouspage",
                            "/warp list page " + Math.max(0, page - 1),
                            ChatFormatting.GRAY,
                            true))
                    .append(Component.literal(" - " + (page + 1) + "/" + totalPages + " - "))
                    .append(createI18nButton("menu.tpatools.warp.button.nextpage",
                            "menu.tpatools.warp.hover.nextpage",
                            "/warp list page " + Math.min(totalPages - 1, page + 1),
                            ChatFormatting.GRAY,
                            true));

            menu.append(pagination).append(Component.literal("\n"));
        } else {
            menu.append(Component.literal("=============================\n")
                    .withStyle(ChatFormatting.GOLD));
        }

        player.sendSystemMessage(menu);
    }

    public static MutableComponent createButton(String text, String hoverText, String command, ChatFormatting color, boolean isClickable) {
        MutableComponent button = Component.literal(text)
                .withStyle(style -> {
                    style = style.withColor(color);
                    if (isClickable) {
                        style = style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
                    } else {
                        style = style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
                    }
                    style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal(hoverText)));
                    return style;
                });
        return button;
    }

    public static MutableComponent createI18nButton(String translationKey, String hoverTranslationKey, String command, ChatFormatting color, boolean isClickable, Object... args) {
        MutableComponent textComponent = ModUtils.translateWithFallback(translationKey, translationKey, args);
        String hoverText = ModUtils.translateWithFallback(hoverTranslationKey, hoverTranslationKey, args).getString();
        return createButton(textComponent.getString(), hoverText, command, color, isClickable);
    }
    public static MutableComponent createI18nButtonWithHoverArgs(String translationKey, String hoverTranslationKey, String command, ChatFormatting color, boolean isClickable, Object[] args, Object[] hoverArgs) {
        MutableComponent textComponent = ModUtils.translateWithFallback(translationKey, translationKey, args);
        String hoverText = ModUtils.translateWithFallback(hoverTranslationKey, hoverTranslationKey, hoverArgs).getString();
        return createButton(textComponent.getString(), hoverText, command, color, isClickable);
    }
}