package com.xinantown.townresearch;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PluginYmlDisplayBusTest {

    @Test
    void pluginYmlSoftDependsOnXiNanTownDisplay() throws Exception {
        Path yml = Path.of("src/main/resources/plugin.yml");
        String text = Files.readString(yml);
        assertTrue(text.contains("softdepend: [XiNanTownDisplay]"), text);
        assertTrue(text.contains("depend: [Towny, Slimefun]"), text);
    }
}
