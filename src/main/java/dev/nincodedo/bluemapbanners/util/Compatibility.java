package dev.nincodedo.bluemapbanners.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
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
                         Gson gson = new GsonBuilder().setPrettyPrinting().create();
                         try (Reader reader = Files.newBufferedReader(path)) {
                             JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                             if ("bluemap-banners".equals(root.get("label").getAsString())) {
                                 root.addProperty("label", "BlueMap Banners");

                                 try (Writer writer = Files.newBufferedWriter(path)) {
                                     gson.toJson(root, writer);
                                 }
                             }
                         }
                     } catch (IOException ignored) {}
                 });
        } catch (IOException ignored) {}
    }
}
