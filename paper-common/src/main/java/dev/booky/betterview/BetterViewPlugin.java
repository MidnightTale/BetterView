package dev.booky.betterview;
// Created by booky10 in BetterView (15:40 03.06.2025)

import dev.booky.betterview.common.BetterViewManager;
import dev.booky.betterview.listener.LevelListener;
import dev.booky.betterview.listener.PlayerListener;
import dev.booky.betterview.listener.TickListener;
import dev.booky.betterview.nms.PaperNmsInterface;
import dev.booky.betterview.platform.PaperBetterView;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import com.tcoded.folialib.FoliaLib;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;

@NullMarked
public class BetterViewPlugin extends JavaPlugin {

    private final BetterViewManager manager;
    private @MonotonicNonNull NamespacedKey listenerKey;
    public static BetterViewPlugin instance;
    public FoliaLib foliaLib;

    public BetterViewPlugin() {
        Path configPath = this.getDataPath().resolve("config.yml");
        this.manager = new BetterViewManager(PaperBetterView::new, configPath);
    }

    @Override
    public void onLoad() {
        this.listenerKey = new NamespacedKey(this, "packets");

        // see https://bstats.org/plugin/bukkit/BetterView/26105
        new Metrics(this, 26105);
    }

    @Override
    public void onEnable() {
        instance = this;
        foliaLib = new FoliaLib(this);
        Bukkit.getPluginManager().registerEvents(new LevelListener(this.manager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this.manager), this);
        Bukkit.getPluginManager().registerEvents(new TickListener(this.manager), this);

        // inject packet handling
        PaperNmsInterface.SERVICE.injectPacketHandler(this.manager, this.listenerKey);

        // run task after server has finished starting
        foliaLib.getScheduler().runNextTick(task -> this.manager.onPostLoad());
    }

    @Override
    public void onDisable() {
        // uninject packet handling
        PaperNmsInterface.SERVICE.uninjectPacketHandler(this.listenerKey);
    }
}
