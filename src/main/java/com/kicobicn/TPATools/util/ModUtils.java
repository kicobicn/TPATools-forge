package com.kicobicn.TPATools.util;

import com.google.common.reflect.TypeToken;
import com.kicobicn.TPATools.Commands.BackHandler;
import com.kicobicn.TPATools.Commands.GraveHandler;
import com.kicobicn.TPATools.Commands.HomeHandler;
import com.kicobicn.TPATools.Commands.RTPHandler;
import com.kicobicn.TPATools.Commands.TPAHandler;
import com.kicobicn.TPATools.Commands.WarpHandler;
import com.kicobicn.TPATools.config.ModConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.kicobicn.TPATools.Commands.TPAHandler.*;
import static com.kicobicn.TPATools.config.ModConfigs.DEFAULT_LANGUAGE;
import static com.kicobicn.TPATools.config.ModConfigs.TIMEOUT_TICKS;

public class ModUtils {

    private static final Logger LOGGER = LogManager.getLogger("TPAtools");

    public static final Map<String, String> translations = new HashMap<>();

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        loadTranslations(DEFAULT_LANGUAGE.get());
        HomeHandler.loadHomes();
        GraveHandler.loadGraves();
        loadToggleStates();
        loadLockedPlayers();
        loadCommandPermissions();
        WarpHandler.loadWarpPoints();
        BackHandler.loadBackPositions();
        RTPHandler.loadCooldowns();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        HomeHandler.saveHomes();
        GraveHandler.saveGraves();
        saveToggleStates();
        saveLockedPlayers();
        saveCommandPermissions();
        WarpHandler.saveWarpPoints();
        BackHandler.saveBackPositions();
        RTPHandler.saveCooldowns();
    }

    public static MutableComponent translateWithFallback(String key, String fallback, Object... args) {
        String translated = translations.getOrDefault(key, fallback);
        Object[] stringArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Component component) {
                stringArgs[i] = component.getString();
            } else {
                stringArgs[i] = args[i];
            }
        }
        String text;
        try {
            text = String.format(translated, stringArgs);
        } catch (RuntimeException e) {
            // 占位符与实参不匹配（例如译文含 %s 但调用方没传参）时退化为纯文本，
            // 避免在服务器 tick 内抛 MissingFormatArgumentException 打断主循环。
            LOGGER.warn("Bad format for translation key {}: {}", key, translated);
            text = translated;
        }
        return Component.literal(text);
    }

    // 加载翻译
    public static void loadTranslations(String lang) {
        translations.clear();
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("tpatools", "lang/" + lang + ".json");
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                var resource = server.getResourceManager().getResource(loc).orElseThrow();
                String jsonContent = new String(resource.open().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> loadedTranslations = GSON.fromJson(jsonContent, new TypeToken<Map<String, String>>(){}.getType());
                translations.putAll(loadedTranslations);
                ModConfigs.DebugLog.info("Loaded translations for language: {}", lang);
            } else {
                LOGGER.warn("Server not available, using fallback translations for {}", lang);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load translations for {}: {}, using fallback", lang, e.getMessage());
        }
    }

    /**
     * 把 identifier 字符串（形如 {@code minecraft:overworld}）解析回 {@link ResourceLocation}。
     * <p>存储层统一用 {@link ResourceLocation#toString()} 写入，读取时必须用本方法还原，
     * 否则会得到 {@code minecraft:minecraft:overworld} 这类非法维度。</p>
     */
    public static ResourceLocation parseIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        }
        String value = raw.trim();
        int split = value.indexOf(':');
        String namespace = split < 0 ? "minecraft" : value.substring(0, split);
        String path = split < 0 ? value : value.substring(split + 1);
        if (namespace.isEmpty()) {
            namespace = "minecraft";
        }
        if (path.isEmpty()) {
            path = "overworld";
        }
        try {
            return ResourceLocation.fromNamespaceAndPath(namespace, path);
        } catch (RuntimeException e) {
            LOGGER.warn("Invalid dimension id '{}', falling back to overworld", raw);
            return ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        }
    }

    /** 统一的 identifier 写库格式 */
    public static String formatIdentifier(ResourceLocation identifier) {
        return identifier == null ? "minecraft:overworld" : identifier.toString();
    }

    /**
     * 获取玩家骑乘的所有实体（包括链式骑乘）
     */
    public static List<Entity> getRiddenEntities(Entity rider) {
        List<Entity> entities = new ArrayList<>();
        Entity current = rider;

        while (current != null) {
            entities.add(current);
            current = current.getVehicle();
        }

        return entities;
    }

    /**
     * 传送玩家及其骑乘的所有实体
     */
    public static void teleportWithRideChain(Entity player, ServerLevel targetLevel, double x, double y, double z, float yRot, float xRot) {
        if (!ModConfigs.ALLOW_TELEPORT_RIDE_ENTITY.get()) {
            // 如果配置禁用骑乘传送，只传送玩家
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.teleportTo(targetLevel, x, y, z, yRot, xRot);
            } else {
                // 对于非玩家实体，使用通用传送方法
                player.unRide();
                teleportEntityToPosition(player, targetLevel, x, y, z, yRot, xRot);
            }
            return;
        }

        // 强制加载原区块3秒，确保传送后原区块不会立即卸载
        if (player.level() instanceof ServerLevel sourceLevel) {
            int chunkX = player.chunkPosition().x;
            int chunkZ = player.chunkPosition().z;
            sourceLevel.setChunkForced(chunkX, chunkZ, true);
            sourceLevel.getServer().tell(new TickTask(sourceLevel.getServer().getTickCount() + 60, () -> {
                sourceLevel.setChunkForced(chunkX, chunkZ, false);
            }));
        }

        // 获取骑乘链中的所有实体
        List<Entity> rideChain = getRiddenEntities(player);

        // 记录所有实体的相对位置和旋转
        Map<Entity, Vec3> relativePositions = new HashMap<>();
        Map<Entity, Float> relativeRotations = new HashMap<>();

        for (int i = 0; i < rideChain.size() - 1; i++) {
            Entity rider = rideChain.get(i);
            Entity vehicle = rideChain.get(i + 1);

            // 计算相对位置和旋转
            Vec3 relativePos = rider.position().subtract(vehicle.position());
            float relativeYRot = rider.getYRot() - vehicle.getYRot();

            relativePositions.put(rider, relativePos);
            relativeRotations.put(rider, relativeYRot);
        }

        // 从最底层的实体开始传送（先传马再传人），
        // 确保玩家客户端加载区块时骑乘实体已在目标区块中
        for (int i = rideChain.size() - 1; i >= 0; i--) {
            Entity entity = rideChain.get(i);

            if (i == rideChain.size() - 1) {
                // 最底层的实体传送到目标位置
                if (entity instanceof ServerPlayer serverPlayer) {
                    serverPlayer.teleportTo(targetLevel, x, y, z, yRot, xRot);
                } else {
                    entity.unRide();
                    teleportEntityToPosition(entity, targetLevel, x, y, z, yRot, xRot);
                }
            } else {
                // 其他实体根据相对位置传送
                Entity vehicle = rideChain.get(i + 1);
                Vec3 relativePos = relativePositions.get(entity);
                float relativeYRot = relativeRotations.get(entity);

                if (relativePos != null) {
                    double newX = vehicle.getX() + relativePos.x;
                    double newY = vehicle.getY() + relativePos.y;
                    double newZ = vehicle.getZ() + relativePos.z;
                    float newYRot = vehicle.getYRot() + relativeYRot;

                    if (entity instanceof ServerPlayer serverPlayer) {
                        serverPlayer.teleportTo(targetLevel, newX, newY, newZ, newYRot, entity.getXRot());
                    } else {
                        entity.unRide();
                        teleportEntityToPosition(entity, targetLevel, newX, newY, newZ, newYRot, entity.getXRot());
                    }
                }
            }
        }

        // 重新建立骑乘关系
        for (int i = 0; i < rideChain.size() - 1; i++) {
            Entity rider = rideChain.get(i);
            Entity vehicle = rideChain.get(i + 1);
            if (rider.isAlive() && vehicle.isAlive() && !rider.isPassenger()) {
                rider.startRiding(vehicle, true);
            }
        }
    }

    /**
     * 传送玩家及其骑乘实体
     */
    public static void teleportWithAllChains(Entity player, ServerLevel targetLevel, double x, double y, double z, float yRot, float xRot) {
        // 首先传送骑乘链
        teleportWithRideChain(player, targetLevel, x, y, z, yRot, xRot);
    }

    /**
     * 传送实体到指定位置（用于非ServerPlayer实体）
     */
    private static void teleportEntityToPosition(Entity entity, ServerLevel targetLevel, double x, double y, double z, float yRot, float xRot) {
        if (entity.level() != targetLevel) {
            // 不同维度，需要跨维度传送
            entity.unRide();
            Entity newEntity = entity.changeDimension(targetLevel);
            newEntity.moveTo(x, y, z, yRot, xRot);
        } else {
            entity.unRide();
            if (entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().removeEntity(entity);
            }
            entity.moveTo(x, y, z, yRot, xRot);
            if (entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().addEntity(entity);
            }
        }
    }

    public static void tick() {
        TeleportDelayManager.tick();

        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, List<TPAHandler.TPARequest>> entry : requests.entrySet()) {
            Iterator<TPAHandler.TPARequest> iterator = entry.getValue().iterator();
            while (iterator.hasNext()) {
                TPAHandler.TPARequest request = iterator.next();
                if (System.currentTimeMillis() - request.timestamp >= TIMEOUT_TICKS.get() * 50) {
                    request.target.sendSystemMessage(translateWithFallback(
                            request.isTPHere ? "command.tpatools.tpahere.timeout" : "command.tpatools.tpa.timeout",
                            "Teleport request from %s has timed out.", request.sender.getName()
                    ));
                    request.sender.sendSystemMessage(translateWithFallback(
                            "command.tpatools.tpa.timeout_self",
                            "Your teleport request to %s has timed out.", request.target.getName()
                    ));
                    iterator.remove();
                    ModConfigs.DebugLog.info("TPA request timed out: {} -> {}",
                            request.sender.getName().getString(), request.target.getName().getString());
                }
            }
            if (entry.getValue().isEmpty()) {
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(requests::remove);
    }

    public static BlockPos findSafeTeleportPosition(ServerLevel level, double x, double y, double z) {
        BlockPos target = BlockPos.containing(x, y, z);

        if (isPositionSafeForTeleport(level, target)) {
            return target;
        }

        for (int radius = 1; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos checkPos = target.offset(dx, dy, dz);
                        if (isPositionSafeForTeleport(level, checkPos)) {
                            return checkPos;
                        }
                    }
                }
            }
        }

        return target;
    }

    private static boolean isPositionSafeForTeleport(ServerLevel level, BlockPos pos) {
        BlockState feetBlock = level.getBlockState(pos);
        BlockState headBlock = level.getBlockState(pos.above());
        BlockState belowBlock = level.getBlockState(pos.below());

        if (feetBlock.canOcclude() || headBlock.canOcclude()) {
            return false;
        }

        if (!belowBlock.blocksMotion()) {
            return false;
        }

        if (feetBlock.is(Blocks.LAVA) || headBlock.is(Blocks.LAVA)
                || belowBlock.is(Blocks.LAVA)) {
            return false;
        }

        return true;
    }
}