package yelf42.picraft;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jspecify.annotations.NonNull;

import java.net.URI;

public class PicraftListeners implements Listener {

    private final Picraft picraft = Picraft.getPlugin(Picraft.class);

    private static final ResourcePackInfo PACK_INFO = ResourcePackInfo.resourcePackInfo()
            .uri(URI.create("https://download.mc-packs.net/pack/af8b1259a67e2f4bdc7a4723d0734577bd423ff6.zip"))
            .hash("af8b1259a67e2f4bdc7a4723d0734577bd423ff6")
            .build();

    public void sendResourcePack(final @NonNull Audience target) {
        final ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(PACK_INFO)
                .prompt(Component.text("Please download this resource pack to use Picraft!"))
                .required(true)
                .build();
        target.sendResourcePacks(request);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sendResourcePack(event.getPlayer());
    }

}
