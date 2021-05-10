package com.momo.worldeditsnapshotcommit;

import net.minecraft.init.Blocks;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

import com.momo.worldeditsnapshotcommit.extension.platform.PlatformManagerAlt;
import com.sk89q.worldedit.WorldEdit;

@Mod(modid = WorldEditSnapshotCommit.MODID, name = WorldEditSnapshotCommit.NAME, version = WorldEditSnapshotCommit.VERSION)
public class WorldEditSnapshotCommit extends JavaPlugin
{
    public static final String MODID = "worldeditsnapshotcommit";
    public static final String NAME = "WorldEdit Snapshot Commit";
    public static final String VERSION = "1.0";

    public static final Logger logger = Logger.getLogger(WorldEdit.class.getCanonicalName());

    private final static WorldEdit instance = WorldEdit.getInstance();

    private final PlatformManagerAlt platformManager = new PlatformManagerAlt(instance);

    public PlatformManagerAlt getPlatformManager() {
        return platformManager;
    }
}
