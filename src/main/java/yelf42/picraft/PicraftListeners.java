package yelf42.picraft;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;

import java.net.URI;

public class PicraftListeners implements Listener {

    private final Picraft picraft = Picraft.getPlugin(Picraft.class);

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Location location = block.getLocation();
        if (picraft.withinPlacement(location, true) != null) event.setCancelled(true);

    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        if (picraft.withinPlacement(location, true) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (picraft.withinPlacement(event.getInteractionPoint(), true) != null) event.setCancelled(true);

    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity.getType() == EntityType.INTERACTION) {
            if (entity.getScoreboardTags().contains("picraft")) {
                if (entity.getScoreboardTags().contains("locked")) return;
                Picross placement = picraft.withinPlacement(entity);
                if (placement == null) return;
                event.setCancelled(true);
                placement.modifyCell(entity.getLocation(), false);
            }
        }
    }



    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager().getType() != EntityType.PLAYER) return;

        Entity entity = event.getEntity();
        if (entity.getType() == EntityType.INTERACTION) {
            if (entity.getScoreboardTags().contains("picraft")) {
                if (entity.getScoreboardTags().contains("locked")) return;
                Picross placement = picraft.withinPlacement(entity);
                if (placement == null) return;
                event.setCancelled(true);
                placement.modifyCell(event.getEntity().getLocation(), true);
            }
        }
    }

}
