package com.hamza.icons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;

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
