package industries.angelic.pastfootsteps.profile;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import industries.angelic.pastfootsteps.PastFootsteps;

/**
 * Bounded, strict local JSON layers with per-file last-good recovery. A decoder runs before the
 * source is remembered, so syntax and semantic failures are both atomic.
 */
public final class LocalJsonOverrides {

    public static final int MAX_BYTES = 1024 * 1024;

    public interface Decoder<T> {

        T decode(JsonElement value) throws Exception;
    }

    private final Map<String, String> lastGood = new LinkedHashMap<String, String>();

    /** Returns null for a missing file or an invalid file with no valid previous version. */
    public <T> T load(File file, String description, Decoder<T> decoder) {
        if (file == null) return null;
        String path = file.getAbsolutePath();
        if (!file.isFile()) {
            lastGood.remove(path);
            return null;
        }
        try {
            if (file.length() > MAX_BYTES) throw new JsonParseException("File exceeds 1 MiB");
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            T value = decoder.decode(read(source));
            lastGood.put(path, source);
            return value;
        } catch (Exception error) {
            PastFootsteps.LOG.warn("Invalid local {}; keeping last valid version", description, error);
            String cached = lastGood.get(path);
            if (cached == null) return null;
            try {
                return decoder.decode(read(cached));
            } catch (Exception stale) {
                lastGood.remove(path);
                PastFootsteps.LOG.warn("Cached local {} is invalid; dropping it", description, stale);
                return null;
            }
        }
    }

    /** Remove cached sources for deleted files below a managed directory. */
    public void dropMissingUnder(File directory) {
        if (directory == null) return;
        String root = directory.getAbsolutePath();
        if (!root.endsWith(File.separator)) root += File.separator;
        Iterator<Map.Entry<String, String>> iterator = lastGood.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            String path = iterator.next()
                .getKey();
            if (path.startsWith(root) && !new File(path).isFile()) iterator.remove();
        }
    }

    /** Strict JSON parser with bounded depth and nodes, including duplicate-key and trailing-data checks. */
    public static JsonElement read(String source) throws IOException {
        // Gson 2.2 rejects top-level primitives in strict mode. An enclosing array permits legal
        // string/null acoustic shorthand while retaining strict syntax and a single root value.
        JsonReader reader = new JsonReader(new StringReader("[" + source + "]"));
        reader.setLenient(false);
        reader.beginArray();
        JsonElement value = read(reader, 0, new int[] { 0 });
        if (reader.hasNext()) throw new JsonParseException("Trailing JSON data");
        reader.endArray();
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new JsonParseException("Trailing JSON data");
        return value;
    }

    private static JsonElement read(JsonReader reader, int depth, int[] nodes) throws IOException {
        if (depth > 32 || ++nodes[0] > 65536) throw new JsonParseException("JSON exceeds complexity limit");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key)) throw new JsonParseException("Duplicate key: " + key);
                    object.add(key, read(reader, depth + 1, nodes));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1, nodes));
                reader.endArray();
                return array;
            case STRING:
                return new JsonPrimitive(reader.nextString());
            case NUMBER:
                return new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN:
                return new JsonPrimitive(reader.nextBoolean());
            case NULL:
                reader.nextNull();
                return JsonNull.INSTANCE;
            default:
                throw new JsonParseException("Unexpected JSON token");
        }
    }
}
