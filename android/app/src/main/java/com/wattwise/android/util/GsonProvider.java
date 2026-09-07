package com.wattwise.android.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Single Gson instance with the adapters the whole app relies on. Lives free of
 * Android imports so repositories and type converters can share it and the pure
 * logic classes stay JVM-testable.
 */
public final class GsonProvider {

    private static final Gson INSTANCE = build();

    private GsonProvider() {
    }

    public static Gson get() {
        return INSTANCE;
    }

    private static Gson build() {
        return new GsonBuilder()
                // Backend serialises LocalDateTime without a zone suffix (UTC semantics).
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();
    }

    /** Serializes/parses {@code yyyy-MM-ddTHH:mm:ss} (ISO_LOCAL_DATE_TIME). */
    private static final class LocalDateTimeAdapter
            implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {

        private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.format(ISO));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (json == null || json.isJsonNull() || json.getAsString().isEmpty()) {
                return null;
            }
            return LocalDateTime.parse(json.getAsString(), ISO);
        }
    }
}