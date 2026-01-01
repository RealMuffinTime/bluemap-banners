package dev.nincodedo.bluemapbanners.storage;

import de.bluecolored.bluemap.api.markers.MarkerSet;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class JdbcStorage implements Storage {
    @Override
    public void addMarker(String world, String pos, Vec3 offset, UUID player, String nearIcon, String farIcon, String text) {

    }

    @Override
    public boolean existsMarker(String world, String pos) {
        return false;
    }

    @Override
    public void removeMarker(String world, String pos) {

    }

    @Override
    public void addWorld(String world) {

    }

    @Override
    public boolean existsWorld(String world) {
        return false;
    }

    @Override
    public void removeWorld(String world) {

    }

    @Override
    public void setMaxMarkerByPlayer(UUID player, int maxMarkers) {

    }

    @Override
    public int getMaxMarkerByPlayer(UUID player) {
        return 0;
    }

    @Override
    public int getMarkerCountByPlayer(UUID player) {
        return 0;
    }

    @Override
    public int getMarkerCountByPlayerByWorld(UUID player, String world) {
        return 0;
    }

    @Override
    public MarkerSet getGeneratedMarkerSet(String world) {
        return new MarkerSet(world);
    }
}
