package com.xinantown.townresearch.display;

import com.xinantown.display.api.DisplayBusService;
import com.xinantown.display.core.BossBarRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResearchDisplayBusBridgeTest {

    private static final UUID PLAYER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void withBus_requestUsesTownresearchSourceAndTownKey() {
        RecordingBus bus = new RecordingBus();
        ResearchDisplayBusBridge bridge = new ResearchDisplayBusBridge(bus);

        bridge.show(PLAYER, "SpawnTown", "§b title", 0.4, 10L);

        assertEquals(1, bus.bossBarRequests.size());
        BossBarRequest req = bus.bossBarRequests.get(0);
        assertEquals("townresearch", req.source());
        assertEquals("SpawnTown", req.key());
        assertEquals("§b title", req.title());
        assertEquals(0.4, req.progress(), 0.0001);
        assertEquals(160L, req.ttlTicks());
        assertEquals(ResearchBossBarPresentation.TTL_TICKS, req.ttlTicks());
        assertTrue(req.ttlTicks() >= 3L * 40L,
                "TTL must span at least three 40-tick refresh intervals");
        assertEquals(10L, bus.lastNowTick);
        assertTrue(bus.clearSourceCalls.isEmpty());
    }

    @Test
    void withBus_repeatedShowRenewsTtlAcrossRefreshIntervals() {
        RecordingBus bus = new RecordingBus();
        ResearchDisplayBusBridge bridge = new ResearchDisplayBusBridge(bus);

        bridge.show(PLAYER, "SpawnTown", "title-a", 0.2, 0L);
        bridge.show(PLAYER, "SpawnTown", "title-b", 0.3, 40L);
        bridge.show(PLAYER, "SpawnTown", "title-c", 0.4, 80L);

        assertEquals(3, bus.bossBarRequests.size());
        for (BossBarRequest req : bus.bossBarRequests) {
            assertEquals(ResearchBossBarPresentation.TTL_TICKS, req.ttlTicks());
            assertEquals("townresearch", req.source());
            assertEquals("SpawnTown", req.key());
        }
        assertEquals("title-c", bus.bossBarRequests.get(2).title());
        assertEquals(0.4, bus.bossBarRequests.get(2).progress(), 0.0001);
        assertEquals(80L, bus.lastNowTick);
    }

    @Test
    void withBus_clearSourceOnHide() {
        RecordingBus bus = new RecordingBus();
        ResearchDisplayBusBridge bridge = new ResearchDisplayBusBridge(bus);

        bridge.hide(PLAYER);

        assertEquals(List.of(PLAYER), bus.clearSourcePlayers);
        assertEquals(List.of("townresearch"), bus.clearSourceCalls);
    }

    @Test
    void withoutBus_delegatesToLegacySink() {
        RecordingLegacy legacy = new RecordingLegacy();
        ResearchDisplayBusBridge bridge = new ResearchDisplayBusBridge(null, legacy);

        bridge.show(PLAYER, "SpawnTown", "title", 0.5, 5L);
        bridge.hide(PLAYER);

        assertEquals(1, legacy.shows.size());
        assertEquals("SpawnTown", legacy.shows.get(0).town());
        assertEquals(List.of(PLAYER), legacy.hides);
    }

    @Test
    void withBusAndLegacy_usesBusOnlyNoDualWrite() {
        RecordingBus bus = new RecordingBus();
        RecordingLegacy legacy = new RecordingLegacy();
        ResearchDisplayBusBridge bridge = new ResearchDisplayBusBridge(bus, legacy);

        bridge.show(PLAYER, "SpawnTown", "§b title", 0.4, 10L);
        bridge.hide(PLAYER);

        assertEquals(1, bus.bossBarRequests.size());
        assertTrue(legacy.shows.isEmpty(), "bus present → no Bukkit dual-write");
        assertEquals(List.of(PLAYER), bus.clearSourcePlayers);
        assertTrue(legacy.hides.isEmpty());
    }

    @Test
    void usesBus_reportsPresence() {
        assertTrue(new ResearchDisplayBusBridge(new RecordingBus()).usesBus());
        assertFalse(new ResearchDisplayBusBridge(null).usesBus());
    }

    private static final class RecordingLegacy implements ResearchDisplayBusBridge.LegacyBossBarSink {
        record Show(UUID playerId, String town, String title, double progress) {}
        final List<Show> shows = new ArrayList<>();
        final List<UUID> hides = new ArrayList<>();

        @Override
        public void show(UUID playerId, String townName, String title, double progress) {
            shows.add(new Show(playerId, townName, title, progress));
        }

        @Override
        public void hide(UUID playerId) {
            hides.add(playerId);
        }

        @Override
        public void cleanupLegacy() {
        }
    }

    private static final class RecordingBus implements DisplayBusService {
        final List<BossBarRequest> bossBarRequests = new ArrayList<>();
        final List<String> clearSourceCalls = new ArrayList<>();
        final List<UUID> clearSourcePlayers = new ArrayList<>();
        long lastNowTick = -1L;

        @Override
        public boolean requestActionBar(UUID playerId, com.xinantown.display.core.ActionBarRequest request, long nowTick) {
            return false;
        }

        @Override
        public Optional<com.xinantown.display.core.ActionBarView> currentActionBar(UUID playerId, long nowTick) {
            return Optional.empty();
        }

        @Override
        public boolean requestBossBar(UUID playerId, BossBarRequest request, long nowTick) {
            bossBarRequests.add(request);
            lastNowTick = nowTick;
            return true;
        }

        @Override
        public Optional<com.xinantown.display.core.BossBarView> currentOwnedBossBar(UUID playerId, long nowTick) {
            return Optional.empty();
        }

        @Override
        public List<com.xinantown.display.core.BossBarEntry> ownedBossBars(UUID playerId, long nowTick) {
            return List.of();
        }

        @Override
        public boolean markExternalBossBarReserved(UUID playerId, boolean reserved) {
            return false;
        }

        @Override
        public void clearPlayer(UUID playerId) {
        }

        @Override
        public void clearSource(UUID playerId, String source) {
            clearSourcePlayers.add(playerId);
            clearSourceCalls.add(source);
        }

        @Override
        public com.xinantown.display.core.PacketEventsCapability packetEvents() {
            return com.xinantown.display.core.PacketEventsCapability.unavailable();
        }

        @Override
        public com.xinantown.display.core.DisplayBus unwrap() {
            return null;
        }
    }
}
