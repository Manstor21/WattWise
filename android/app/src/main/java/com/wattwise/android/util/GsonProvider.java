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
 * Instancia única de Gson con los adaptadores de los que depende toda la app.
 * Libre de imports de Android para que los repositorios y conversores de tipos puedan
 * compartirla y las clases de lógica pura sigan siendo testeables en la JVM.
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
                // El backend serializa LocalDateTime sin sufijo de zona (semántica UTC).
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();
    }

    /** Serializa/deserializa {@code yyyy-MM-ddTHH:mm:ss} (ISO_LOCAL_DATE_TIME). */
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