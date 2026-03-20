package yelf42.picraft;

import com.google.gson.Gson;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Picraft extends JavaPlugin {

    private final List<Picross> activePlacements = new ArrayList<>();

    public static PicraftConfig CONFIG;

    @Override
    public void onEnable() {
        // Resource pack
        File packFile = new File(getDataFolder(), "picraft.zip");
        if (!packFile.exists()) {
            saveResource("picraft.zip", false);
        }

        getServer().getPluginManager().registerEvents(new PicraftListeners(), this);

        // Picross folder
        getDataFolder().mkdirs();
        File crosswordsFolder = new File(getDataFolder(), "picross");
        crosswordsFolder.mkdirs();

        loadPicraftConfig();
        loadPlacements();
        registerCommands();


    }

    @Override
    public void onDisable() {
        savePicraftConfig();
        savePlacements();
    }

    private void registerCommands() {
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            //commands.registrar().register(fetch);
        });
    }

    private record PlacementData(String world, int x, int y, int z, int width, int height, String encoding, String id, String hintId) {}
    private void loadPlacements() {
        File file = new File(getDataFolder(), "placements.json");
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            PlacementData[] placements = new Gson().fromJson(reader, PlacementData[].class);
            for (PlacementData data : placements) {

                World world = Bukkit.getWorld(data.world());
                if (world == null) {
                    getLogger().warning("Could not find world: " + data.world());
                    continue;
                }
                Location location = new Location(world, data.x(), data.y(), data.z());
                Picross placement = new Picross(data.width(), data.height(), data.encoding(), location, UUID.fromString(data.id()), UUID.fromString(data.hintId()));
                placement.startTicking(this);

                activePlacements.add(placement);
            }
            getLogger().info("Loaded " + activePlacements.size() + " picross placements.");
        } catch (Exception e) {
            getLogger().severe("Failed to load placements: " + e.getMessage());
        }
    }
    private void savePlacements() {
        File file = new File(getDataFolder(), "placements.json");
        List<PlacementData> data = activePlacements.stream()
                .map(p -> new PlacementData(
                        p.getLocation().getWorld().getName(),
                        p.getLocation().getBlockX(),
                        p.getLocation().getBlockY(),
                        p.getLocation().getBlockZ(),
                        p.getWidth(),
                        p.getHeight(),
                        p.getEncoding(),
                        p.getId().toString(),
                        p.getHintTextDisplay().toString()
                ))
                .toList();
        try (FileWriter writer = new FileWriter(file)) {
            new Gson().toJson(data, writer);
        } catch (IOException e) {
            getLogger().severe("Failed to save placements: " + e.getMessage());
        }
    }

    private void savePicraftConfig() {
        File file = new File(getDataFolder(), "picraft_config.json");
        try (FileWriter writer = new FileWriter(file)) {
            new Gson().toJson(CONFIG, writer);
        } catch (IOException e) {
            getLogger().severe("Failed to save config: " + e.getMessage());
        }
    }

    private void loadPicraftConfig() {
        File file = new File(getDataFolder(), "picraft_config.json");
        if (!file.exists()) {
            CONFIG = new PicraftConfig(1,1);
        } else {
            try (FileReader reader = new FileReader(file)) {
                CONFIG = new Gson().fromJson(reader, PicraftConfig.class);
                CONFIG = new PicraftConfig(Math.max(1, CONFIG.minHint()), Math.max(CONFIG.minHint(), CONFIG.maxHint()));
            } catch (IOException e) {
                getLogger().severe("Failed to load config: " + e.getMessage());
            }
        }
    }
}
