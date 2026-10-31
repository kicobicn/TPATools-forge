package com.kicobicn.TPATools.chat;

import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.util.ModUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatHandler {
    private static final Logger LOGGER = LogManager.getLogger("TPAtools");

    /** &1 ~ &9：展示快捷栏对应槽位的物品 */
    private static final Pattern ITEM_PATTERN = Pattern.compile("&([1-9])");

    /** 判断单词边界用的标点集合（提及只在“完整单词”上生效，避免 Ann 命中 Anna） */
    private static final String PUNCTUATION = " \t\n,，.。!！?？:：;；\"'“”‘’()（）[]【】<>《》-_/\\|+*~^%$#@=";

    /**
     * 用 Forge 的 {@link ServerChatEvent} 直接改写消息组件：
     * 不需要取消原消息再手动广播，控制台日志与其它聊天处理链都不受影响。
     */
    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        try {
            ServerPlayer player = event.getPlayer();
            String originalMessage = event.getRawText();
            ModConfigs.DebugLog.info("Processing chat message from {}: {}",
                    player.getName().getString(), originalMessage);

            event.setMessage(buildMessage(originalMessage, player));
        } catch (Exception e) {
            LOGGER.error("Error processing chat message", e);
        }
    }

    /**
     * 把原始聊天文本转换为带样式/物品组件的结果。
     * <p>一次扫描同时处理「@提及」和「&1~&9 物品展示」两个功能。</p>
     */
    private MutableComponent buildMessage(String raw, ServerPlayer sender) {
        List<Object> parts = new ArrayList<>();
        Set<UUID> pinged = new HashSet<>();

        Matcher itemMatcher = ITEM_PATTERN.matcher(raw);
        int lastIndex = 0;
        while (itemMatcher.find()) {
            if (itemMatcher.start() > lastIndex) {
                appendMentionParts(parts, raw.substring(lastIndex, itemMatcher.start()), sender, pinged);
            }

            int slot = Integer.parseInt(itemMatcher.group(1)) - 1;
            ItemStack itemStack = sender.getInventory().getItem(slot);
            if (!itemStack.isEmpty()) {
                parts.add(createItemComponent(itemStack));
                ModConfigs.DebugLog.info("Replaced &{} with item: {}", slot + 1,
                        itemStack.getDisplayName().getString());
            } else {
                parts.add(itemMatcher.group(0));
            }
            lastIndex = itemMatcher.end();
        }

        if (lastIndex < raw.length()) {
            appendMentionParts(parts, raw.substring(lastIndex), sender, pinged);
        }

        MutableComponent component = Component.literal("");
        for (Object part : parts) {
            if (part instanceof Component componentPart) {
                component.append(componentPart);
            } else {
                component.append(Component.literal(String.valueOf(part)));
            }
        }
        return component;
    }

    /**
     * 按空白与标点切分文本，对「完整单词」形式的在线玩家名标色并发送提醒。
     */
    private void appendMentionParts(List<Object> parts, String text, ServerPlayer sender, Set<UUID> pinged) {
        if (text.isEmpty()) {
            return;
        }

        int index = 0;
        int length = text.length();
        while (index < length) {
            // 跳过前导标点
            int tokenStart = index;
            while (tokenStart < length && isPunctuation(text.charAt(tokenStart))) {
                tokenStart++;
            }
            if (tokenStart > index) {
                parts.add(text.substring(index, tokenStart));
            }
            if (tokenStart >= length) {
                break;
            }

            int tokenEnd = tokenStart;
            while (tokenEnd < length && !isPunctuation(text.charAt(tokenEnd))) {
                tokenEnd++;
            }

            String token = text.substring(tokenStart, tokenEnd);
            ServerPlayer mentioned = resolveMention(token, sender);
            if (mentioned != null) {
                parts.add(Component.literal(token).withStyle(ChatFormatting.AQUA));
                if (pinged.add(mentioned.getUUID())) {
                    notifyMention(mentioned, sender);
                }
            } else {
                parts.add(token);
            }
            index = tokenEnd;
        }
    }

    private boolean isPunctuation(char c) {
        return PUNCTUATION.indexOf(c) >= 0;
    }

    private ServerPlayer resolveMention(String token, ServerPlayer sender) {
        String name = token.startsWith("@") ? token.substring(1) : token;
        if (name.isEmpty()) {
            return null;
        }
        for (ServerPlayer candidate : sender.getServer().getPlayerList().getPlayers()) {
            if (candidate.getUUID().equals(sender.getUUID())) {
                continue;
            }
            if (candidate.getName().getString().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }

    private void notifyMention(ServerPlayer target, ServerPlayer sender) {
        if (target.connection == null) {
            return;
        }
        ModConfigs.DebugLog.info("Player {} mentioned {}", sender.getName().getString(),
                target.getName().getString());

        ClientboundSetTitleTextPacket titlePacket = new ClientboundSetTitleTextPacket(
                ModUtils.translateWithFallback(
                        "command.tpatools.chat.mention",
                        "%s mentioned you",
                        sender.getName()).withStyle(ChatFormatting.AQUA));
        ClientboundSetTitlesAnimationPacket timesPacket = new ClientboundSetTitlesAnimationPacket(10, 40, 10);
        target.connection.send(timesPacket);
        target.connection.send(titlePacket);

        target.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // 展示物品组件
    private MutableComponent createItemComponent(ItemStack itemStack) {
        MutableComponent itemDisplayName = itemStack.getDisplayName().copy();
        return Component.literal("[")
                .withStyle(ChatFormatting.GOLD)
                .append(itemDisplayName)
                .append(Component.literal("]").withStyle(ChatFormatting.GOLD));
    }
}
