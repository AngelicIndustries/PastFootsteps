package org.fentanylsolutions.pastfootsteps.profile;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/** Strict bounded JSON reader shared by structured consumer profiles. */
public final class JsonData {

    private JsonData() {}

    public static JsonObject read(Reader source) throws IOException {
        JsonReader reader = new JsonReader(source);
        reader.setLenient(false);
        JsonObject value = object(read(reader, 0, new int[] { 0 }));
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new JsonParseException("Trailing JSON data");
        return value;
    }

    private static JsonElement read(JsonReader reader, int depth, int[] nodes) throws IOException {
        if (depth > 32 || ++nodes[0] > 65536) throw new JsonParseException("JSON profile exceeds complexity limit");
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

    public static JsonObject object(JsonElement element) {
        if (element == null || !element.isJsonObject()) throw new JsonParseException("Expected object");
        return element.getAsJsonObject();
    }

    public static void keys(JsonObject object, String... allowed) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!Arrays.asList(allowed)
                .contains(entry.getKey())) throw new JsonParseException("Unknown field: " + entry.getKey());
        }
    }

    public static double number(JsonObject object, String key, double fallback, double min, double max) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive()
            .isNumber()) throw new JsonParseException(key + " must be numeric");
        double result = value.getAsDouble();
        if (!Double.isFinite(result) || result < min || result > max)
            throw new JsonParseException(key + " out of range");
        return result;
    }

    public static String string(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive()
            .isString()) throw new JsonParseException(key + " must be a string");
        return value.getAsString();
    }

    public static boolean bool(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive()
            .isBoolean()) throw new JsonParseException(key + " must be boolean");
        return value.getAsBoolean();
    }
}
