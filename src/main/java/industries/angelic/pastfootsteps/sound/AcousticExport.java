package industries.angelic.pastfootsteps.sound;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/** Writes a portable resource pack containing a selected acoustic-definition snapshot. */
public final class AcousticExport {

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final String LICENSE = "assets/presencefootsteps/LICENSE-MIT.txt";
    private static final String NOTICE = "assets/presencefootsteps/SOURCE-NOTICE.txt";
    private static final String INFO = "This pack contains selected PastFootsteps acoustic definitions only.\n"
        + "It contains no audio, sounds.json, block mappings, entity profiles, or CFG settings.\n"
        + "The definitions require PastFootsteps and the source sound packs that provide their sound events.\n"
        + "Local config/pastfootsteps/acoustics overrides still take priority over an installed export.\n"
        + "The included MIT license and source notice describe bundled PF definitions.\n"
        + "Definitions from other resource packs or local overrides retain their own authorship and terms.\n"
        + "Only successful loaded definitions are exported; unapplied disk edits are not included.\n"
        + "Exporter version: "
        + industries.angelic.pastfootsteps.Tags.VERSION
        + "\n";

    private AcousticExport() {}

    /** Exports definitions without changing game options or installing the resulting pack. */
    public static int write(File target, Map<String, String> definitions) throws IOException {
        if (target == null) throw new IllegalArgumentException("target must not be null");
        if (definitions == null || definitions.isEmpty())
            throw new IllegalArgumentException("acoustic snapshot must not be empty");

        Gson gson = new GsonBuilder().setPrettyPrinting()
            .serializeNulls()
            .create();
        for (String id : definitions.keySet()) {
            if (id == null || "index".equals(id)
                || !ID.matcher(id)
                    .matches())
                throw new IllegalArgumentException("Invalid acoustic ID: " + id);
        }
        Map<String, byte[]> entries = new TreeMap<String, byte[]>();
        JsonArray index = new JsonArray();
        for (Map.Entry<String, String> definition : new TreeMap<String, String>(definitions).entrySet()) {
            String id = definition.getKey();
            JsonElement json = parse(definition.getValue(), id);
            index.add(new JsonPrimitive(id));
            entries.put("assets/presencefootsteps/config/acoustics/" + id + ".json", utf8(gson.toJson(json) + "\n"));
        }
        entries.put("assets/presencefootsteps/config/acoustics/index.json", utf8(gson.toJson(index) + "\n"));
        entries.put("pack.mcmeta", utf8(gson.toJson(packMetadata()) + "\n"));
        entries.put("EXPORT-INFO.txt", utf8(INFO));
        entries.put("LICENSE-MIT.txt", resource(LICENSE));
        entries.put("SOURCE-NOTICE.txt", resource(NOTICE));

        Path destination = target.toPath()
            .toAbsolutePath();
        Path parent = destination.getParent();
        if (parent == null) throw new IOException("Target has no parent directory: " + target);
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, target.getName(), ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary);
                ZipOutputStream zip = new ZipOutputStream(output)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    ZipEntry zipEntry = new ZipEntry(entry.getKey());
                    zipEntry.setTime(0L);
                    zip.putNextEntry(zipEntry);
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return definitions.size();
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static JsonElement parse(String value, String id) throws IOException {
        if (value == null) throw new IOException("Missing acoustic definition: " + id);
        if (value.trim()
            .isEmpty()) throw new IOException("Empty acoustic definition: " + id);
        try {
            JsonElement result = new JsonParser().parse(value);
            return result;
        } catch (RuntimeException error) {
            throw new IOException("Invalid acoustic definition: " + id, error);
        }
    }

    private static JsonElement packMetadata() {
        JsonElement result = new JsonParser()
            .parse("{\"pack\":{\"pack_format\":1,\"description\":\"PastFootsteps acoustic definitions\"}}");
        return result;
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] resource(String name) throws IOException {
        InputStream input = AcousticExport.class.getClassLoader()
            .getResourceAsStream(name);
        if (input == null) throw new IOException("Missing bundled export resource: " + name);
        try (InputStream stream = input) {
            return readAll(stream);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        byte[] buffer = new byte[4096];
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }
}
