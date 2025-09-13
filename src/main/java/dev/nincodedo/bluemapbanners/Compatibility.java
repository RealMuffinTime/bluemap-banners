package dev.nincodedo.bluemapbanners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class Compatibility {

    /**
     * Apply compatibility fixes for old versions of BlueMap Banners
     */
    public static void init(String mapsConfigFolder) {
        applyMarkerListRename(mapsConfigFolder);
    }

    /**
     * Rename marker list from "bluemap-banners" to "BlueMap Banners"
     */
    public static void applyMarkerListRename(String mapsConfigFolder) {
        if (mapsConfigFolder == null || mapsConfigFolder.isBlank()) return;

        Path folder = Paths.get(mapsConfigFolder);
        if (!Files.isDirectory(folder)) return;

        try (Stream<Path> files = Files.walk(folder)) {
            files.filter(Files::isRegularFile)
                 .forEach(path -> {
                     try {
                         ObjectMapper mapper = new ObjectMapper();
                         File jsonFile = new File(path.toUri());
                         ObjectNode root = (ObjectNode) mapper.readTree(jsonFile);

                         if (root.get("label").asText().equals("bluemap-banners")) {
                             root.put("label", "BlueMap Banners");

                             mapper.writer().writeValue(jsonFile, root);
                         }
                     } catch (IOException ignored) {}
                 });
        } catch (IOException ignored) {}
    }
}
