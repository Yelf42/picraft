package yelf42.picraft;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static yelf42.picraft.PicrossSolver.generateUniquePicross;

public class Picross {
    private final Location location;
    private BukkitTask tickTask;

    private final int width;
    private final int height;
    private final String encoding;

    private final List<List<Integer>> across;
    private final List<List<Integer>> down;

    private final List<List<Integer>> playGrid;
    private final int[] correctDown;
    private final int[] correctAcross;
    private boolean solved = false;

    private final UUID id;
    private final String tag;

    private final BoundingBox boundingBox;

    public Picross(int width, int height, String encoding, Location location, UUID id, boolean solved) {
        this(width, height, encoding, location, id);
        this.solved = solved;
    }

    public Picross(int width, int height, String encoding, Location location) {
        this(width, height, encoding, location, UUID.randomUUID());
    }

    public Picross(int width, int height, String encoding, Location location, UUID id) {
        this.location = location;
        this.width = width;
        this.height = height;

        this.encoding = encoding;
        this.across = readAcrossEncoding(new BigInteger(encoding, 16), width, height);
        this.down = readDownEncoding(new BigInteger(encoding, 16), width, height);

        this.correctDown = Arrays.stream(new int[width]).map(i -> 0).toArray();
        this.correctAcross = Arrays.stream(new int[height]).map(i -> 0).toArray();
        this.playGrid = IntStream.range(0, height)
                .mapToObj(r -> new ArrayList<>(Collections.nCopies(width, 0)))
                .collect(Collectors.toList());

        this.id = id;
        this.tag = id.toString().replace("-", "");

        this.boundingBox = new BoundingBox(location.getBlockX(), location.getBlockY() - 2, location.getBlockZ(),
                location.getBlockX() + width, location.getBlockY() + 3, location.getBlockZ() + height);
    }

    private void print(BigInteger grid, int width, int height) {
        Bukkit.getLogger().info("TARGET GRID:");
        for (int row = 0; row < height; row++) {
            StringBuilder out = new StringBuilder();
            for (int col = 0; col < width; col++) {
                int bitIndex = row * width + col;
                out.append(grid.testBit(bitIndex) ? "1" : "0");
                out.append(" ");
            }
            Bukkit.getLogger().info(out.toString());
        }
    }

    public void print() {
        Bukkit.getLogger().info("PLAY GRID:");
        for (int row = 0; row < height; row++) {
            StringBuilder out = new StringBuilder();
            for (int col = 0; col < width; col++) {
                out.append(""+this.playGrid.get(row).get(col));
                out.append(" ");
            }
            Bukkit.getLogger().info(out.toString());
        }
    }

    public static List<List<Integer>> readAcrossEncoding(BigInteger grid, int width, int height) {
        List<List<Integer>> across = new ArrayList<>();
        for (int row = 0; row < height; row++) {
            List<Integer> column = new ArrayList<>();
            int counter = 0;

            for (int col = 0; col < width; col++) {
                int bitIndex = row * width + col;
                if (grid.testBit(bitIndex)) {
                    counter++;
                } else {
                    if (counter > 0) {
                        column.add(counter);
                        counter = 0;
                    }
                }
            }
            if (counter > 0) column.add(counter);
            across.add(column);
        }
        return across;
    }

    public static List<List<Integer>> readDownEncoding(BigInteger grid, int width, int height) {
        List<List<Integer>> down = new ArrayList<>();
        for (int col = 0; col < width; col++) {
            List<Integer> along = new ArrayList<>();
            int counter = 0;

            for (int row = 0; row < height; row++) {
                int bitIndex = row * width + col;
                if (grid.testBit(bitIndex)) {
                    counter++;
                } else {
                    if (counter > 0) {
                        along.add(counter);
                        counter = 0;
                    }
                }
            }
            if (counter > 0) along.add(counter);
            down.add(along);
        }
        return down;
    }

    public Location getLocation() {
        return location;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getEncoding() {
        return encoding;
    }

    public UUID getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    public boolean getSolved() {
        return solved;
    }

    public void readCells() {
        if (solved) return;
        int minChunkX = (int) this.boundingBox.getMinX() >> 4;
        int minChunkZ = (int) this.boundingBox.getMinZ() >> 4;
        int maxChunkX = (int) this.boundingBox.getMaxX() >> 4;
        int maxChunkZ = (int) this.boundingBox.getMaxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                this.location.getWorld().getChunkAt(cx, cz).load();
            }
        }

        this.location.getWorld().getEntities().forEach(entity -> {
            if (entity.getScoreboardTags().contains(this.tag) && entity.getScoreboardTags().contains("cell")) {
                TextDisplay td = (TextDisplay) entity;
                Location location = td.getLocation();
                Pair<Integer, Integer> cell = locationToCell(location);

                if (td.getScoreboardTags().contains("filled")) {
                    addCell(cell.getLeft(), cell.getRight());
                }
            }
        });

        //print();
    }

    public void modifyCell(Location location, boolean hit) {
        Pair<Integer, Integer> cell = locationToCell(location);
        Entity entity = this.location.getWorld().getNearbyEntities(location, 1, 1, 1, (e -> e.getType() == EntityType.TEXT_DISPLAY && e.getScoreboardTags().contains(cell.getLeft() + "," + cell.getRight())))
                .stream().findFirst().orElse(null);
        if (entity == null) {
            entity = spawnAnswerDisplay(location.getBlockX(), location.getBlockY(), location.getBlockZ(), cell.getLeft() + "," + cell.getRight(), hit);
        }
        TextDisplay td = (TextDisplay) entity;
        Component text = Component.text("■");
        if (!hit) {
            td.text(text.color(TextColor.color(0x00b7c9)));
            td.setTextOpacity((byte) 255);
            td.addScoreboardTag("filled");
            addCell(cell.getLeft(), cell.getRight());
        } else {
            td.text(text.color(TextColor.color(0x6c898c)));
            td.setTextOpacity((byte) 100);
            td.removeScoreboardTag("filled");
            delCell(cell.getLeft(), cell.getRight());
        }
        //print();
    }

    private void addCell(int row, int col) {
        if (this.playGrid.get(row).get(col) == 1) return;

        this.playGrid.get(row).set(col, 1);
        this.correctAcross[row] = checkRow(row) ? 1 : 0;
        this.correctDown[col] = checkCol(col) ? 1 : 0;

        validate();
    }

    private void delCell(int row, int col) {
        if (this.playGrid.get(row).get(col) == 0) return;

        this.playGrid.get(row).set(col, 0);
        this.correctAcross[row] = checkRow(row) ? 1 : 0;
        this.correctDown[col] = checkCol(col) ? 1 : 0;

        validate();
    }
    private void validate() {
        boolean won = Arrays.stream(this.correctAcross).allMatch(b -> b == 1)
                && Arrays.stream(this.correctDown).allMatch(b -> b == 1);
        if (won) {
            this.location.getWorld().getNearbyEntities(this.boundingBox.clone().expand(1,0,1)).forEach(entity -> {
                if (entity.getScoreboardTags().contains(this.tag)) {
                    if (entity.getType() == EntityType.INTERACTION) {
                        entity.addScoreboardTag("locked");
                    } else if (entity instanceof TextDisplay td && td.getScoreboardTags().contains("cell") && td.getScoreboardTags().contains("filled")) {
                        Component text = Component.text("■");
                        td.text(text.color(NamedTextColor.DARK_GREEN));
                    }
                }
            });
            spawnFireworks();
            this.solved = true;
        }
    }

    private boolean checkRow(int row) {
        int clueIdx = 0;
        int counter = 0;
        for (int col = 0; col < this.width; col++) {
            if (this.playGrid.get(row).get(col) == 1) {
                counter++;
            } else {
                if (counter > 0) {
                    if ((clueIdx >= this.across.get(row).size() || counter != this.across.get(row).get(clueIdx++))) return false;
                    counter = 0;
                }
            }
        }
        if (counter > 0 && (clueIdx >= this.across.get(row).size() || counter != this.across.get(row).get(clueIdx++))) return false;
        return clueIdx == this.across.get(row).size();
    }

    private boolean checkCol(int col) {
        int clueIdx = 0;
        int counter = 0;
        for (int row = 0; row < this.height; row++) {
            if (this.playGrid.get(row).get(col) == 1) {
                counter++;
            } else {
                if (counter > 0) {
                    if (clueIdx >= this.down.get(col).size() || counter != this.down.get(col).get(clueIdx++)) return false;
                    counter = 0;
                }
            }
        }
        if (counter > 0 && (clueIdx >= this.down.get(col).size() || counter != this.down.get(col).get(clueIdx++))) return false;
        return clueIdx == this.down.get(col).size();
    }

    // Starts the 3-tick update loop
    public void startTicking(Picraft plugin) {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 5L);
    }

    // Stops the update loop
    public void stopTicking() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void tick() {
        this.location.getWorld().getNearbyEntities(this.boundingBox).forEach(entity -> {
            if (entity instanceof Player player) {
                int col = player.getLocation().getBlockX() - this.location.getBlockX();
                int row = player.getLocation().getBlockZ() - this.location.getBlockZ();

                // Check bounds
                if (row < 0 || row >= this.height || col < 0 || col >= this.width) return;

                List<Integer> across = this.across.get(row);
                List<Integer> down = this.down.get(col);

                if (player.isSneaking()) {
                    player.sendActionBar(Component.text("Down: " + down.stream().map(String::valueOf).collect(Collectors.joining(" ")), NamedTextColor.BLUE, TextDecoration.BOLD));
                } else {
                    player.sendActionBar(Component.text("Across: " + across.stream().map(String::valueOf).collect(Collectors.joining(" ")), NamedTextColor.RED, TextDecoration.BOLD));
                }
            }
        });
    }

    public void generate() {
        World world = this.location.getWorld();
        for (int row = -1; row < height + 1; row++) {
            for (int col = -1; col < width + 1; col++) {
                world.setBlockData(this.location.getBlockX() + col, this.location.getBlockY(), this.location.getBlockZ() + row, Material.AIR.createBlockData());
                world.setBlockData(this.location.getBlockX() + col, this.location.getBlockY() + 1, this.location.getBlockZ() + row, Material.AIR.createBlockData());
                world.setBlockData(this.location.getBlockX() + col, this.location.getBlockY() + 2, this.location.getBlockZ() + row, Material.AIR.createBlockData());

                if ((row >= 0 && row < height) && (col >= 0 && col < width)) {
                    world.setBlockData(this.location.getBlockX() + col, this.location.getBlockY() - 1, this.location.getBlockZ() + row, Material.WHITE_CONCRETE.createBlockData());
                    spawnGridLines(this.location.getBlockX() + col + 1.0f, this.location.getBlockY(), this.location.getBlockZ() + row + 1.0f);
                    placeAnswerInteractions(this.location.getBlockX() + col, this.location.getBlockY(), this.location.getBlockZ() + row);
                } else {
                    world.setBlockData(this.location.getBlockX() + col, this.location.getBlockY() - 1, this.location.getBlockZ() + row, Material.BLACK_CONCRETE.createBlockData());
                }
            }
        }
        spawnDownClueBoard();
        spawnAcrossClueBoard();
    }

    private Pair<Integer, Integer> locationToCell(Location location) {
        return new ImmutablePair<>(
                location.getBlockZ() - this.location.getBlockZ(), // row
                location.getBlockX() - this.location.getBlockX()  // col
        );
    }

    public boolean isInsideGridLined(Location location) {
        return (location.getWorld() == this.location.getWorld() && this.boundingBox.clone().expand(1, 0, 1).contains(location.getX(),location.getY(),location.getZ()));
    }

    public boolean isInsideGrid(Location location, boolean edge) {
        return (edge) ? isInsideGridLined(location) : isInsideGrid(location);
    }

    public boolean isInsideGrid(Location location) {
        return (location.getWorld() == this.location.getWorld() && this.boundingBox.contains(location.getX(),location.getY(),location.getZ()));
    }

    public boolean isInsideGrid(Entity entity) {
        return (entity.getWorld() == this.location.getWorld() && this.boundingBox.contains(entity.getX(),entity.getY(),entity.getZ()));
    }

    public boolean overlappingBoundingBox(BoundingBox bb, World world) {
        return this.boundingBox.overlaps(bb.expand(2)) && this.location.getWorld() == world;
    }

    public void removeTextDisplays() {
        int minChunkX = (int) this.boundingBox.getMinX() >> 4;
        int minChunkZ = (int) this.boundingBox.getMinZ() >> 4;
        int maxChunkX = (int) this.boundingBox.getMaxX() >> 4;
        int maxChunkZ = (int) this.boundingBox.getMaxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                this.location.getWorld().getChunkAt(cx, cz).load();
            }
        }

        this.location.getWorld().getEntities().forEach(entity -> {
            if (entity.getScoreboardTags().contains(this.tag)) {
                entity.remove();
            }
        });
    }

    private static final Transformation TRANSFORM_E = new Transformation(
            new Vector3f(-0.035f, -0.6f, 0.0f),
            new AxisAngle4f(0, 0, 0, 1),
            new Vector3f(0.5f, 8.0f, 1.0f),
            new AxisAngle4f(0, 0, 0, 1)
    );

    private static final Transformation TRANSFORM_W = new Transformation(
            new Vector3f(-0.975f, -0.6f, 0.0f),
            new AxisAngle4f(0, 0, 0, 1),
            new Vector3f(0.5f, 8.0f, 1.0f),
            new AxisAngle4f(0, 0, 0, 1)
    );

    private static final Transformation TRANSFORM_S = new Transformation(
            new Vector3f(0.6f, 0.025f, 0.0f),
            new AxisAngle4f((float) Math.PI / 2, 0, 0, 1),
            new Vector3f(0.5f, 8.0f, 1.0f),
            new AxisAngle4f(0, 0, 0, 1)
    );

    private static final Transformation TRANSFORM_N = new Transformation(
            new Vector3f(0.6f, 0.965f, 0.0f),
            new AxisAngle4f((float) Math.PI / 2, 0, 0, 1),
            new Vector3f(0.5f, 8.0f, 1.0f),
            new AxisAngle4f(0, 0, 0, 1)
    );

    private void spawnGridLines(float x, float y, float z) {
        Location location = new Location(this.location.getWorld(), x, y, z);
        for (Transformation transform : new Transformation[]{TRANSFORM_E, TRANSFORM_W, TRANSFORM_S, TRANSFORM_N}) {
            this.location.getWorld().spawn(location, TextDisplay.class, display -> {
                display.text(Component.text("■").color(NamedTextColor.BLACK));
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setDefaultBackground(false);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setShadowed(false);
                display.setSeeThrough(false);
                display.setLineWidth(200);
                display.setTransformation(transform);
                display.setRotation(0.0f, -90.0f);
                display.addScoreboardTag(this.tag);
                display.addScoreboardTag("picraft");
                display.setPersistent(true);
            });
        }
    }

    public void placeAnswerInteractions(double x, double y, double z) {
        Location location = new Location(this.location.getWorld(), x + 0.5, y, z + 0.5);

        Interaction interaction = (Interaction) this.location.getWorld().spawnEntity(location, EntityType.INTERACTION);
        interaction.setInteractionWidth(0.8f);
        interaction.setInteractionHeight(0.1f);
        interaction.setResponsive(true);
        interaction.addScoreboardTag(this.tag);
        interaction.addScoreboardTag("picraft");
    }

    private static final Transformation TRANSFORM_A = new Transformation(
            new Vector3f(1-0.585f, -1.46f, 0.0f),
            new AxisAngle4f(0, 0, 0, 1),
            new Vector3f(7.0f, 7.0f, 1.0f),
            new AxisAngle4f(0, 0, 0, 1)
    );

    public Entity spawnAnswerDisplay(float x, float y, float z, String cell, boolean hit) {
        Location location = new Location(this.location.getWorld(), x, y, z);
        return this.location.getWorld().spawn(location, TextDisplay.class, display -> {
            display.text(Component.text("■").color(hit ? TextColor.color(0x6c898c) : TextColor.color(0x00b7c9)));
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setShadowed(false);
            display.setSeeThrough(false);
            display.setLineWidth(200);
            display.setTransformation(TRANSFORM_A);
            display.setRotation(0.0f, -90.0f);
            display.addScoreboardTag(this.tag);
            display.addScoreboardTag("picraft");
            display.addScoreboardTag("cell");
            display.addScoreboardTag(cell);
            if (!hit) display.addScoreboardTag("filled");
            if (hit) display.setTextOpacity((byte) 100);
            display.setPersistent(true);
        });
    }

    private void spawnFireworks() {
        FireworkEffect effect = FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL)
                .withColor(Color.GREEN)
                .withFade(Color.LIME)
                .trail(true)
                .flicker(true)
                .build();

        World world = this.location.getWorld();
        List<Location> corners = List.of(
                new Location(world, this.boundingBox.getMinX(), this.location.getBlockY(), this.boundingBox.getMinZ()),
                new Location(world, this.boundingBox.getMaxX(), this.location.getBlockY(), this.boundingBox.getMinZ()),
                new Location(world, this.boundingBox.getMinX(), this.location.getBlockY(), this.boundingBox.getMaxZ()),
                new Location(world, this.boundingBox.getMaxX(), this.location.getBlockY(), this.boundingBox.getMaxZ())
        );

        for (Location corner : corners) {
            Firework fw = world.spawn(corner, Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(effect);
            meta.setPower(0);
            fw.setFireworkMeta(meta);
        }
    }

    private void spawnDownClueBoard() {
        float max = 1.0f + (this.down.stream().mapToInt(List::size).max().orElse(2) / 2.0f);

        for (int x = this.location.getBlockX(); x < this.location.getBlockX() + this.width; x++) {
            Location l = new Location(this.location.getWorld(), x + 0.5f, this.location.getBlockY(), this.location.getBlockZ() - 0.5);
            this.location.getWorld().spawn(l, TextDisplay.class, display -> {
                display.text(Component.text("■").color(NamedTextColor.BLACK));
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setDefaultBackground(false);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setShadowed(false);
                display.setSeeThrough(false);
                display.setTransformation(new Transformation(
                        new Vector3f(-0.1f, -0.6f * max, 0.0f),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(8.0f, 8.0f * max, 8.0f),
                        new AxisAngle4f(0, 0, 0, 1)
                ));
                display.addScoreboardTag(this.tag);
                display.addScoreboardTag("picraft");
                display.setPersistent(true);
            });

            this.location.getWorld().spawn(l, TextDisplay.class, display -> {
                display.text(Component.text("■").color(TextColor.color(0xcdd3d4)));
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setDefaultBackground(false);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setShadowed(false);
                display.setSeeThrough(false);
                display.setTransformation(new Transformation(
                        new Vector3f(-0.1f, -0.6f * max, 0.001f),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(7.0f, 8.0f * max, 8.0f),
                        new AxisAngle4f(0, 0, 0, 1)
                ));
                display.addScoreboardTag(this.tag);
                display.addScoreboardTag("picraft");
                display.setPersistent(true);
            });

            Component clue = Component.text(this.down.get(x - this.location.getBlockX()).stream().map(String::valueOf).collect(Collectors.joining("\n"))).color(NamedTextColor.DARK_BLUE);
            this.location.getWorld().spawn(l, TextDisplay.class, display -> {
                display.text(clue);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setDefaultBackground(false);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setShadowed(false);
                display.setSeeThrough(false);
                display.setTransformation(new Transformation(
                        new Vector3f(-0.05f, 0.1f, 0.002f),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(2.0f, 2.0f, 8.0f),
                        new AxisAngle4f(0, 0, 0, 1)
                ));
                display.addScoreboardTag(this.tag);
                display.addScoreboardTag("picraft");
                display.setPersistent(true);
            });

        }
    }

    private void spawnAcrossClueBoard() {
        float max = 1.0f + (this.across.stream().mapToInt(List::size).max().orElse(2) / 2.0f);

        for (int z = this.location.getBlockZ(); z < this.location.getBlockZ() + this.height; z++) {
            Location l = new Location(this.location.getWorld(), this.location.getBlockX() - 0.5, this.location.getBlockY(), z + 0.5);
            this.location.getWorld().spawn(l, TextDisplay.class, display -> {
                display.text(Component.text("■").color(NamedTextColor.BLACK));
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setDefaultBackground(false);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setShadowed(false);
                display.setSeeThrough(false);
                display.setTransformation(new Transformation(
                        new Vector3f(-0.1f, -0.6f * max, 0.0f),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(8.0f, 8.0f * max, 8.0f),
                        new AxisAngle4f(0, 0, 0, 1)
                ));
                display.setRotation(-90.0F, 0);
                display.addScoreboardTag(this.tag);
                display.addScoreboardTag("picraft");
                display.setPersistent(true);
            });

            this.location.getWorld().spawn(l, TextDisplay.class, display -> {
                display.text(Component.text("■").color(TextColor.color(0xcdd3d4)));
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setDefaultBackground(false);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setShadowed(false);
                display.setSeeThrough(false);
                display.setTransformation(new Transformation(
                        new Vector3f(-0.1f, -0.6f * max, 0.001f),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(7.0f, 8.0f * max, 8.0f),
                        new AxisAngle4f(0, 0, 0, 1)
                ));
                display.setRotation(-90.0F, 0);
                display.addScoreboardTag(this.tag);
                display.addScoreboardTag("picraft");
                display.setPersistent(true);
            });

            Component clue = Component.text(this.across.get(z - this.location.getBlockZ()).stream().map(String::valueOf).collect(Collectors.joining("\n"))).color(NamedTextColor.DARK_RED);
            this.location.getWorld().spawn(l, TextDisplay.class, display -> {
                display.text(clue);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setDefaultBackground(false);
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setShadowed(false);
                display.setSeeThrough(false);
                display.setTransformation(new Transformation(
                        new Vector3f(-0.05f, 0.1f, 0.002f),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(2.0f, 2.0f, 8.0f),
                        new AxisAngle4f(0, 0, 0, 1)
                ));
                display.setRotation(-90.0F, 0);
                display.addScoreboardTag(this.tag);
                display.addScoreboardTag("picraft");
                display.setPersistent(true);
            });

        }
    }

}
