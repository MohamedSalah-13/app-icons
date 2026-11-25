package com.hamza.icons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

import java.util.function.BiConsumer;

/** Loaded only when a caller supplies a manifest template. */
final class PwaTemplate {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT)
            .serializeNulls().disableHtmlEscaping().setPrettyPrinting().create();

    private final JsonObject result;

    PwaTemplate(String template) {
        try {
            result = JSON.fromJson(template, JsonObject.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Invalid manifest template: " + e.getMessage(), e);
        }
        if (result == null) {
            throw new IllegalArgumentException("The manifest template must be a JSON object");
        }
        validateFields(result);
    }

    private static void validateFields(JsonObject manifest) {
        stringFields(manifest, "", "name", "short_name", "description", "id", "start_url", "scope",
                "display", "orientation", "lang", "dir", "theme_color", "background_color");
        objectArray(manifest, "", "icons", PwaTemplate::imageFields);
        objectArray(manifest, "", "screenshots", (image, path) -> {
            imageFields(image, path);
            stringFields(image, path, "label", "form_factor", "platform");
        });
        objectArray(manifest, "", "shortcuts", (shortcut, path) -> {
            stringFields(shortcut, path, "name", "short_name", "description", "url");
            objectArray(shortcut, path, "icons", PwaTemplate::imageFields);
        });
        objectArray(manifest, "", "related_applications", (app, path) -> stringFields(app, path, "platform", "id", "url"));
        if (manifest.has("prefer_related_applications")) {
            JsonElement value = manifest.get("prefer_related_applications");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                throw wrongType("prefer_related_applications", "boolean");
            }
        }
        arrayItems(manifest, "", "categories", PwaTemplate::requireString);
        arrayItems(manifest, "", "display_override", (item, path) -> {
            if (item.isJsonObject()) {
                JsonObject entry = item.getAsJsonObject();
                stringFields(entry, path + ".", "display");
                if (entry.has("url_patterns")) {
                    array(entry.get("url_patterns"), path + ".url_patterns");
                }
            } else if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                throw wrongType(path, "string or object");
            }
        });
    }

    private static void imageFields(JsonObject image, String prefix) {
        stringFields(image, prefix, "src", "type", "sizes", "purpose");
    }

    private static void stringFields(JsonObject object, String prefix, String... fields) {
        for (String field : fields) {
            if (object.has(field)) {
                requireString(object.get(field), prefix + field);
            }
        }
    }

    private static void requireString(JsonElement value, String path) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw wrongType(path, "string");
        }
    }

    private static JsonArray array(JsonElement value, String path) {
        if (!value.isJsonArray()) {
            throw wrongType(path, "array");
        }
        return value.getAsJsonArray();
    }

    private static void objectArray(JsonObject parent, String prefix, String field,
                                    BiConsumer<JsonObject, String> validate) {
        arrayItems(parent, prefix, field, (item, path) -> {
            if (!item.isJsonObject()) {
                throw wrongType(path, "object");
            }
            validate.accept(item.getAsJsonObject(), path + ".");
        });
    }

    private static void arrayItems(JsonObject parent, String prefix, String field,
                                   BiConsumer<JsonElement, String> validate) {
        if (!parent.has(field)) {
            return;
        }
        String path = prefix + field;
        JsonArray items = array(parent.get(field), path);
        for (int i = 0; i < items.size(); i++) {
            validate.accept(items.get(i), path + "[" + i + "]");
        }
    }

    private static IllegalArgumentException wrongType(String field, String expected) {
        return new IllegalArgumentException("Invalid manifest template field '" + field + "': expected " + expected);
    }

    String merge(String defaults) {
        JsonObject generated = JSON.fromJson(defaults, JsonObject.class);
        if (result.has("name") && !result.has("short_name")) {
            result.add("short_name", result.get("name"));
        }
        for (var entry : generated.entrySet()) {
            if ("icons".equals(entry.getKey()) || !result.has(entry.getKey())) {
                result.add(entry.getKey(), entry.getValue());
            }
        }
        return JSON.toJson(result) + "\n";
    }
}
