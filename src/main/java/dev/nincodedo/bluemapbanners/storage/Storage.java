package dev.nincodedo.bluemapbanners.storage;

import de.bluecolored.bluemap.api.markers.MarkerSet;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public interface Storage {

    void addMarker(String world, String pos, Vec3 offset, UUID player, String nearIcon, String farIcon, String text);

    boolean existsMarker(String world, String pos);

    void removeMarker(String world, String pos);

    void addWorld(String world);

    boolean existsWorld(String world);

    void removeWorld(String world);

    void setMaxMarkerByPlayer(UUID player, int maxMarkers);

    int getMaxMarkerByPlayer(UUID player);

    int getMarkerCountByPlayer(UUID player);

    int getMarkerCountByPlayerByWorld(UUID player, String world);

    MarkerSet getGeneratedMarkerSet(String world);
}
