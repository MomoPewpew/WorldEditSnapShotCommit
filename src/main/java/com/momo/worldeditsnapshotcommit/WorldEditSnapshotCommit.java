package com.momo.worldeditsnapshotcommit;

import net.minecraft.init.Blocks;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import com.sk89q.worldedit.LocalPlayer;

@Mod(modid = WorldEditSnapshotCommit.MODID, name = WorldEditSnapshotCommit.NAME, version = WorldEditSnapshotCommit.VERSION)
public class WorldEditSnapshotCommit extends JavaPlugin
{
    public static final String MODID = "worldeditsnapshotcommit";
    public static final String NAME = "WorldEdit Snapshot Commit";
    public static final String VERSION = "1.0";

    private static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();
    }

    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        // some example code
        logger.info("DIRT BLOCK >> {}", Blocks.DIRT.getRegistryName());
    }
}
