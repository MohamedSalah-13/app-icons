package com.hamza.icons;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PwaTemplateTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"name":7}                                        | name                      | string
            {"short_name":null}                               | short_name                | string
            {"start_url":[]}                                  | start_url                 | string
            {"display":false}                                 | display                   | string
            {"background_color":{}}                           | background_color          | string
            {"shortcuts":"inbox"}                             | shortcuts                 | array
            {"shortcuts":[{},null]}                           | shortcuts[1]              | object
            {"shortcuts":[{"url":42}]}                        | shortcuts[0].url          | string
            {"shortcuts":[{"icons":{}}]}                      | shortcuts[0].icons        | array
            {"shortcuts":[{"icons":[{"src":false}]}]}         | shortcuts[0].icons[0].src | string
            {"icons":false}                                   | icons                     | array
            {"icons":[{"sizes":[] }]}                         | icons[0].sizes            | string
            {"screenshots":[{"label":false}]}                 | screenshots[0].label      | string
            {"related_applications":[1]}                      | related_applications[0]   | object
            {"prefer_related_applications":"true"}            | prefer_related_applications | boolean
            {"categories":["news",5]}                         | categories[1]             | string
            {"display_override":[true]}                       | display_override[0]       | string or object
            """)
    void rejectsIncorrectJsonTypes(String template, String path, String type) {
        var error = assertThrows(IllegalArgumentException.class, () -> new PwaTemplate(template));
        assertEquals("Invalid manifest template field '" + path + "': expected " + type, error.getMessage());
    }
}
