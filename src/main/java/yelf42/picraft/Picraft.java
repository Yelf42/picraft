package yelf42.picraft;

import com.google.gson.Gson;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static yelf42.picraft.PicrossSolver.generateUniquePicross;

public final class Picraft extends JavaPlugin {

    private final List<Picross> activePlacements = new ArrayList<>();
    public Picross withinPlacement(Entity entity) {
        return activePlacements.stream()
                .filter(placement -> placement.isInsideGrid(entity)).findFirst().orElse(null);
    }
    public Picross withinPlacement(Location location) {
        return activePlacements.stream()
                .filter(placement -> placement.isInsideGrid(location)).findFirst().orElse(null);
    }
    public Picross withinPlacement(Location location, boolean edge) {
        return activePlacements.stream()
                .filter(placement -> placement.isInsideGrid(location, edge)).findFirst().orElse(null);
    }
    public boolean checkPlacementOverlap(BoundingBox bb, World world) {
        return activePlacements.stream().anyMatch((crosswordPlacement -> crosswordPlacement.overlappingBoundingBox(bb, world)));
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new PicraftListeners(), this);

        loadPlacements();
        registerCommands();
    }

    @Override
    public void onDisable() {
        savePlacements();
    }

    private void registerCommands() {
        LiteralCommandNode<CommandSourceStack> remove = Commands.literal("remove").requires(sender -> sender.getSender().isOp())
                .then(Commands.argument("dimension", ArgumentTypes.world())
                        .then(Commands.argument("pos", ArgumentTypes.blockPosition())
                                .executes(ctx -> {
                                    World world = ctx.getArgument("dimension", World.class);
                                    BlockPositionResolver posResolver = ctx.getArgument("pos", BlockPositionResolver.class);
                                    BlockPosition pos = posResolver.resolve(ctx.getSource());
                                    Location location = new Location(world, pos.blockX(), pos.blockY(), pos.blockZ());

                                    // Emergency entity removal
                                    if (withinPlacement(location) == null) {
                                        Entity entity = world.getNearbyEntities(location, 2, 1, 2, (e -> e.getType() == EntityType.TEXT_DISPLAY)).stream().findFirst().orElse(null);
                                        if (entity != null && entity.getScoreboardTags().contains("picraft")) {
                                            String tag = entity.getScoreboardTags().stream().filter((s) -> !Objects.equals(s, "picraft")).findFirst().orElse("");
                                            if (!tag.isBlank()) {
                                                world.getEntities().forEach(e -> {
                                                    if (e.getScoreboardTags().contains(tag)) {
                                                        e.remove();
                                                    }
                                                });
                                            }
                                        }

                                        return Command.SINGLE_SUCCESS;
                                    }

                                    activePlacements.removeIf(placement -> {
                                        if (placement.isInsideGrid(location)) {
                                            placement.stopTicking();
                                            placement.removeTextDisplays();
                                            return true;
                                        }
                                        return false;
                                    });

                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();

        LiteralCommandNode<CommandSourceStack> buildUnique = Commands.literal("buildUnique").requires(sender -> sender.getSender().isOp())
                .then(Commands.argument("dimension", ArgumentTypes.world())
                        .then(Commands.argument("pos", ArgumentTypes.blockPosition())
                                .then(Commands.argument("width", IntegerArgumentType.integer(5, 24))
                                        .then(Commands.argument("height", IntegerArgumentType.integer(5, 24))
                                                .executes(ctx -> {
                                                    CommandSender sender = ctx.getSource().getSender();

                                                    World world = ctx.getArgument("dimension", World.class);
                                                    BlockPositionResolver posResolver = ctx.getArgument("pos", BlockPositionResolver.class);
                                                    BlockPosition position = posResolver.resolve(ctx.getSource());
                                                    Location location = new Location(world, position.x(), position.y(), position.z());

                                                    int width = ctx.getArgument("width", Integer.class);
                                                    int height = ctx.getArgument("height", Integer.class);


                                                    if (checkPlacementOverlap(new BoundingBox(location.getBlockX(), location.getBlockY() - 2, location.getBlockZ(),
                                                            location.getBlockX() + width, location.getBlockY() + 3, location.getBlockZ() + height), world)) {
                                                        sender.sendMessage(Component.text("Overlaps existing placement, cancelling build"));
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                                                        String encoding = PicrossSolver.generateUniquePicross(width, height);
                                                        Bukkit.getScheduler().runTask(this, () -> {
                                                            Picross placement = new Picross(width, height, encoding, location);
                                                            activePlacements.add(placement);
                                                            placement.startTicking(this);
                                                            placement.generate();
                                                            sender.sendMessage(Component.text("Puzzle ready! Encoding: " + encoding));
                                                        });
                                                    });

                                                    return Command.SINGLE_SUCCESS;
                                                })))))
                .build();

        LiteralCommandNode<CommandSourceStack> buildRandom = Commands.literal("buildRandom").requires(sender -> sender.getSender().isOp())
                .then(Commands.argument("dimension", ArgumentTypes.world())
                        .then(Commands.argument("pos", ArgumentTypes.blockPosition())
                                .then(Commands.argument("width", IntegerArgumentType.integer(5, 24))
                                        .then(Commands.argument("height", IntegerArgumentType.integer(5, 24))
                                                .executes(ctx -> {
                                                    CommandSender sender = ctx.getSource().getSender();

                                                    World world = ctx.getArgument("dimension", World.class);
                                                    BlockPositionResolver posResolver = ctx.getArgument("pos", BlockPositionResolver.class);
                                                    BlockPosition position = posResolver.resolve(ctx.getSource());
                                                    Location location = new Location(world, position.x(), position.y(), position.z());

                                                    int width = ctx.getArgument("width", Integer.class);
                                                    int height = ctx.getArgument("height", Integer.class);


                                                    if (checkPlacementOverlap(new BoundingBox(location.getBlockX(), location.getBlockY() - 2, location.getBlockZ(),
                                                            location.getBlockX() + width, location.getBlockY() + 3, location.getBlockZ() + height), world)) {
                                                        sender.sendMessage(Component.text("Overlaps existing placement, cancelling build"));
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                                                        String encoding = PicrossSolver.generateRandomPicross(width, height);
                                                        Bukkit.getScheduler().runTask(this, () -> {
                                                            Picross placement = new Picross(width, height, encoding, location);
                                                            activePlacements.add(placement);
                                                            placement.startTicking(this);
                                                            placement.generate();
                                                            sender.sendMessage(Component.text("Puzzle ready! Encoding: " + encoding));
                                                        });
                                                    });

                                                    return Command.SINGLE_SUCCESS;
                                                })))))
                .build();

        LiteralCommandNode<CommandSourceStack> buildExisting = Commands.literal("buildExisting").requires(sender -> sender.getSender().isOp())
                .then(Commands.argument("dimension", ArgumentTypes.world())
                        .then(Commands.argument("pos", ArgumentTypes.blockPosition())
                                .then(Commands.argument("width", IntegerArgumentType.integer(5, 24))
                                        .then(Commands.argument("height", IntegerArgumentType.integer(5, 24))
                                                .then(Commands.argument("encoding", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    CommandSender sender = ctx.getSource().getSender();

                                                    World world = ctx.getArgument("dimension", World.class);
                                                    BlockPositionResolver posResolver = ctx.getArgument("pos", BlockPositionResolver.class);
                                                    BlockPosition position = posResolver.resolve(ctx.getSource());
                                                    Location location = new Location(world, position.x(), position.y(), position.z());

                                                    int width = ctx.getArgument("width", Integer.class);
                                                    int height = ctx.getArgument("height", Integer.class);
                                                    String encoding = ctx.getArgument("encoding", String.class);

                                                    if (!isValidEncoding(encoding, width, height)) {
                                                        sender.sendMessage(Component.text("Encoding is invalid for the given width/height, cancelling build"));
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    if (checkPlacementOverlap(new BoundingBox(location.getBlockX(), location.getBlockY() - 2, location.getBlockZ(),
                                                            location.getBlockX() + width, location.getBlockY() + 3, location.getBlockZ() + height), world)) {
                                                        sender.sendMessage(Component.text("Overlaps existing placement, cancelling build"));
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                                                        Bukkit.getScheduler().runTask(this, () -> {
                                                            Picross placement = new Picross(width, height, encoding, location);
                                                            activePlacements.add(placement);
                                                            placement.startTicking(this);
                                                            placement.generate();
                                                            sender.sendMessage(Component.text("Puzzle ready! Encoding: " + encoding));
                                                        });
                                                    });

                                                    return Command.SINGLE_SUCCESS;
                                                }))))))
                .build();

        LiteralCommandNode<CommandSourceStack> buildRepeat = Commands.literal("buildRepeat").requires(sender -> sender.getSender().isOp())
                .then(Commands.argument("dimension", ArgumentTypes.world())
                        .then(Commands.argument("pos", ArgumentTypes.blockPosition())
                                                .executes(ctx -> {
                                                    CommandSender sender = ctx.getSource().getSender();

                                                    World world = ctx.getArgument("dimension", World.class);
                                                    BlockPositionResolver posResolver = ctx.getArgument("pos", BlockPositionResolver.class);
                                                    BlockPosition position = posResolver.resolve(ctx.getSource());
                                                    Location location = new Location(world, position.x(), position.y(), position.z());

                                                    if (activePlacements.isEmpty()) {
                                                        sender.sendMessage(Component.text("No nonogram to repeat, cancelling build"));
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    String encoding = activePlacements.getLast().getEncoding();
                                                    int width = activePlacements.getLast().getWidth();
                                                    int height = activePlacements.getLast().getHeight();

                                                    if (checkPlacementOverlap(new BoundingBox(location.getBlockX(), location.getBlockY() - 2, location.getBlockZ(),
                                                            location.getBlockX() + width, location.getBlockY() + 3, location.getBlockZ() + height), world)) {
                                                        sender.sendMessage(Component.text("Overlaps existing placement, cancelling build"));
                                                        return Command.SINGLE_SUCCESS;
                                                    }

                                                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                                                        Bukkit.getScheduler().runTask(this, () -> {
                                                            Picross placement = new Picross(width, height, encoding, location);
                                                            activePlacements.add(placement);
                                                            placement.startTicking(this);
                                                            placement.generate();
                                                            sender.sendMessage(Component.text("Puzzle ready! Encoding: " + encoding));
                                                        });
                                                    });

                                                    return Command.SINGLE_SUCCESS;
                                                })))
                .build();

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(remove);
            commands.registrar().register(buildUnique);
            commands.registrar().register(buildRandom);
            commands.registrar().register(buildExisting);
            commands.registrar().register(buildRepeat);
        });
    }

    public static boolean isValidEncoding(String encoding, int width, int height) {
        try {
            BigInteger grid = new BigInteger(encoding, 16);
            return grid.bitLength() <= width * height;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private record PlacementData(String world, int x, int y, int z, int width, int height, String encoding, String id, boolean solved) {}
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
                Picross placement = new Picross(data.width(), data.height(), data.encoding(), location, UUID.fromString(data.id()), data.solved());
                placement.startTicking(this);
                placement.readCells();
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
                        p.getSolved()
                ))
                .toList();
        try (FileWriter writer = new FileWriter(file)) {
            new Gson().toJson(data, writer);
        } catch (IOException e) {
            getLogger().severe("Failed to save placements: " + e.getMessage());
        }
    }
}
