package com.xinantown.townresearch;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.object.Town;
import com.xinantown.townresearch.model.ResearchLab;
import com.xinantown.townresearch.model.TownResearch;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class ResearchStorageFeedbackTest {
    @TempDir Path directory;

    @BeforeAll
    static void initializeTownTypeWithoutARealServer() throws Exception {
        try (var settings = mockStatic(TownySettings.class)) {
            settings.when(TownySettings::getTownAccountPrefix).thenReturn("town-");
            Class.forName(Town.class.getName());
        }
    }

    @Test
    void commandReportsBadStorageAndPreservesTheFile() throws Exception {
        var manager = new TownDataManager(directory.toFile(), Logger.getLogger("test"));
        var plugin = mock(TownResearchPlugin.class);
        when(plugin.getDataManager()).thenReturn(manager);
        var settings = mock(ResearchSettings.class);
        when(settings.getMaxLabs()).thenReturn(5);
        var command = new ResearchCommand(plugin, new ResearchGuiListener(manager, 5),
                mock(SlimefunBridge.class), settings);
        var player = mock(Player.class);
        var town = mock(Town.class);
        when(town.getName()).thenReturn("Town");
        var api = mock(TownyAPI.class);
        when(api.getTown(player)).thenReturn(town);

        try (var towny = mockStatic(TownyAPI.class)) {
            towny.when(TownyAPI::getInstance).thenReturn(api);
            for (String content : List.of("town_name: Town\nstorage_version: 99\n",
                    "town_name: [broken\n", "town_name: Town\npaused_projects: broken\n")) {
                Files.writeString(directory.resolve("town.yml"), content);
                clearInvocations(player);
                assertTrue(command.onCommand(player, null, "research", new String[]{"list"}));
                verify(player).sendMessage(contains("请联系管理员核对"));
                verify(player, never()).sendMessage(startsWith("§a"));
                assertEquals(content, Files.readString(directory.resolve("town.yml")));
            }
        }
    }

    @Test
    void cachedFileChangedToFutureVersionRejectsStartBeforeBankAccess() throws Exception {
        var manager = new TownDataManager(directory.toFile(), Logger.getLogger("test"));
        var research = new TownResearch("Town", 5);
        research.addLab(new ResearchLab("world", 1, 2));
        manager.save("Town", research);
        String future = "town_name: Town\nstorage_version: 99\n";
        Files.writeString(directory.resolve("town.yml"), future);
        var town = mock(Town.class);
        when(town.getName()).thenReturn("Town");
        var settings = mock(ResearchSettings.class);
        when(settings.getMaxLabs()).thenReturn(5);
        var slimefun = mock(SlimefunBridge.class);

        String result = new ResearchService(manager, slimefun, settings)
                .startResearch(town, mock(Player.class), "sf:test");

        assertNotNull(result);
        assertTrue(result.contains("勿重复付费操作"));
        verify(town, never()).getAccount();
        verifyNoInteractions(slimefun);
        assertTrue(research.getActiveProjects().isEmpty());
        assertEquals(future, Files.readString(directory.resolve("town.yml")));
    }

    @Test
    void ioFailureIsReportedInsteadOfCachingFalseSuccess() throws Exception {
        Path blockedParent = directory.resolve("not-a-directory");
        Files.writeString(blockedParent, "preserve");
        var manager = new TownDataManager(blockedParent.toFile(), Logger.getLogger("test"));

        var failure = assertThrows(TownDataManager.StorageException.class,
                () -> manager.save("Town", new TownResearch("Town", 5)));

        assertTrue(failure.playerMessage().contains("本次操作未确认完成"));
        assertNull(manager.load("Town", 5));
        assertEquals("preserve", Files.readString(blockedParent));
    }

    @Test
    void refusedStorageDoesNotGrantOrRevokeResearcherPermissions() throws Exception {
        var manager = new TownDataManager(directory.toFile(), Logger.getLogger("test"));
        var original = UUID.randomUUID();
        var added = UUID.randomUUID();
        var research = new TownResearch("Town", 5);
        research.addResearcher(original);
        manager.save("Town", research);
        var gui = new ResearchGuiListener(manager, 5);
        gui.loadResearchers("Town");
        String future = "town_name: Town\nstorage_version: 99\n";
        Files.writeString(directory.resolve("town.yml"), future);

        assertThrows(TownDataManager.StorageException.class, () -> gui.addResearcher("Town", added));
        assertThrows(TownDataManager.StorageException.class, () -> gui.removeResearcher("Town", original));

        var api = mock(TownyAPI.class);
        var player = mock(Player.class);
        try (var towny = mockStatic(TownyAPI.class)) {
            towny.when(TownyAPI::getInstance).thenReturn(api);
            when(player.getUniqueId()).thenReturn(original);
            assertTrue(gui.isResearcher("Town", player));
            when(player.getUniqueId()).thenReturn(added);
            assertFalse(gui.isResearcher("Town", player));
        }
        assertEquals(future, Files.readString(directory.resolve("town.yml")));
    }
}
