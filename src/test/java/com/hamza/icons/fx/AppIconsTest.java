package com.hamza.icons.fx;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The names only: making a JavaFX image needs a graphics toolkit, which a build server may not have. */
class AppIconsTest {

    @Test
    void thePatternStandsForOneResourceAtEachWindowSize() {
        assertEquals(List.of("/img/icon-16.png", "/img/icon-24.png", "/img/icon-32.png", "/img/icon-48.png",
                "/img/icon-64.png", "/img/icon-128.png", "/img/icon-256.png"), AppIcons.names("/img/icon-%d.png"));
    }

    @Test
    void theSizesCanBeChosen() {
        assertEquals(List.of("app-16.png", "app-256.png"), AppIcons.names("app-%d.png", 16, 256));
    }

    @Test
    void refusesAPatternWithoutASize() {
        assertThrows(IllegalArgumentException.class, () -> AppIcons.names("/img/icon.png"));
    }
}
