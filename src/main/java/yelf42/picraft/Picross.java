package yelf42.picraft;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.math.BigInteger;
import java.util.*;

public class Picross {
    private final Location location;
    private BukkitTask tickTask;

    private final int width;
    private final int height;
    private final String encoding;

    private List<List<Integer>> across;
    private List<List<Integer>> down;

    private final UUID id;
    private final String tag;

    private Set<UUID> requestedHint = new HashSet<>();
    private UUID hintTextDisplay;

    private BoundingBox boundingBox;

    public Picross(int width, int height, Location location) {
        this(width, height, newPicross(width, height), location, UUID.randomUUID(), null);
    }

    public Picross(int width, int height, String encoding, Location location) {
        this(width, height, encoding, location, UUID.randomUUID(), null);
    }

    public Picross(int width, int height, String encoding, Location location, UUID id, UUID hintId) {
        this.location = location;
        this.width = width;
        this.height = height;

        this.encoding = encoding;
        this.across = readAcrossEncoding(new BigInteger(encoding, 16), width, height);
        this.down = readDownEncoding(new BigInteger(encoding, 16), width, height);

        this.id = id;
        this.tag = id.toString().replace("-", "");
        this.hintTextDisplay = hintId;

        this.boundingBox = new BoundingBox(location.getBlockX(), location.getBlockY() - 2, location.getBlockZ(),
                location.getBlockX() + width, location.getBlockY() + 3, location.getBlockZ() + height);
    }

    private static String newPicross(int width, int height) {
        BigInteger grid = new BigInteger(width * height, new Random());
        return grid.toString(16);
    }

    private static List<List<Integer>> readAcrossEncoding(BigInteger grid, int width, int height) {
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

    private static List<List<Integer>> readDownEncoding(BigInteger grid, int width, int height) {
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

    public UUID getHintTextDisplay() {
        return hintTextDisplay;
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

    }

    private void generate() {

    }
}
