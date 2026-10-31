package com.kicobicn.TPATools;

import com.kicobicn.TPATools.Commands.*;
import com.kicobicn.TPATools.chat.ChatHandler;
import com.kicobicn.TPATools.config.ModConfigs;
import com.kicobicn.TPATools.database.DatabaseManager;
import com.kicobicn.TPATools.util.ModUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(TPATools.MODID)
public class TPATools {

    public static final String MODID = "tpatools";

    public TPATools() {
        // 注册模组事件总线
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onConfigLoaded);
        
        // 注册事件监听器
        MinecraftForge.EVENT_BUS.register(TPAHandler.class);
        MinecraftForge.EVENT_BUS.register(BackHandler.class);
        MinecraftForge.EVENT_BUS.register(HomeHandler.class);
        MinecraftForge.EVENT_BUS.register(GraveHandler.class);
        MinecraftForge.EVENT_BUS.register(RTPHandler.class);
        MinecraftForge.EVENT_BUS.register(WarpHandler.class);
        MinecraftForge.EVENT_BUS.register(ModConfigs.class);
        MinecraftForge.EVENT_BUS.register(ModUtils.class);
        MinecraftForge.EVENT_BUS.register(new ChatHandler());
        // 让本类里的 @SubscribeEvent 生效（此前从未注册，导致关库逻辑是死代码）
        MinecraftForge.EVENT_BUS.register(TPATools.class);
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            public void onServerTick(TickEvent.ServerTickEvent event) {
                if (event.phase == TickEvent.Phase.END) {
                    ModUtils.tick();
                }
            }
        });
        
        // 注册配置文件
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModConfigs.CONFIG, "tpatools/tpatool.toml");

        // 获取配置目录
        ModConfigs.getConfigDir();
    }

    // 配置加载完成事件
    private void onConfigLoaded(ModConfigEvent.Loading event) {
        // 在配置加载完成后初始化数据库
        DatabaseManager.initialize();
    }

    // 服务器已停止：此时 ModUtils 的 save*() 已经执行完，再关库才不会丢数据
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DatabaseManager.close();
    }
}