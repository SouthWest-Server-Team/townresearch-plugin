package com.xinantown.townresearch.display;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 案 1：research BossBar 的唯一出口是直写（{@link ResearchDisplayBusBridge.LegacyBossBarSink}）。
 *
 * <p>本测试文件**故意不引用 display 的任何类**：既是「不再借 display」的活证据，
 * 也是编译期钉子 —— 谁把 display 依赖加回来，这个文件会先编译失败。
 * 旧形状的 {@code withBus_*} 用例（有总线就跳过直写）随用户拍板一起消失。
 */
class ResearchDisplayBusBridgeTest {

    private static final UUID PLAYER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void showEmitsTheProgressBarThroughTheDirectSink() {
        RecordingLegacy legacy = new RecordingLegacy();
        ResearchDisplayBusBridge bridge = new ResearchDisplayBusBridge(legacy);

        bridge.show(PLAYER, "SpawnTown", "§b title", 0.4);

        assertEquals(1, legacy.shows.size(), "show 必须直接把进度条发给直写 sink");
        RecordingLegacy.Show show = legacy.shows.get(0);
        assertEquals(PLAYER, show.playerId());
        assertEquals("SpawnTown", show.town());
        assertEquals("§b title", show.title());
        assertEquals(0.4, show.progress(), 0.0001);
    }

    /**
     * 退服（{@code PlayerQuitEvent → hidePlayer → bridge.hide}）与插件 disable（{@code cleanup}）
     * 都不许抛出任何异常 —— 尤其是 {@link Error} 家族的 {@code NoSuchMethodError} / {@code NoClassDefFoundError}，
     * 它们会绕过 {@code catch (Exception)} 直接冒到事件线程。
     */
    @Test
    void hideClearsTheBarAndNeverThrowsOnPlayerQuit() {
        RecordingLegacy legacy = new RecordingLegacy();
        ResearchDisplayBusBridge bridge = new ResearchDisplayBusBridge(legacy);

        assertDoesNotThrow(() -> bridge.hide(PLAYER),
                "退服路径不许抛任何异常（NoSuchMethodError / NoClassDefFoundError 都是 Error，catch(Exception) 接不住）");
        assertEquals(List.of(PLAYER), legacy.hides, "hide 必须落到直写 sink，BossBar 才会被清");
    }

    @Test
    void lookupWiresTheDirectSinkForBothShowAndHide() {
        RecordingLegacy legacy = new RecordingLegacy();

        ResearchDisplayBusBridge bridge = ResearchDisplayBusBridge.lookup(legacy);
        bridge.show(PLAYER, "SpawnTown", "title", 0.5);
        bridge.hide(PLAYER);

        assertEquals(1, legacy.shows.size(), "装配入口（lookup）必须落在直写路径上");
        assertEquals("SpawnTown", legacy.shows.get(0).town());
        assertEquals(List.of(PLAYER), legacy.hides);
    }

    @Test
    void cleanupDelegatesToTheDirectSink() {
        RecordingLegacy legacy = new RecordingLegacy();
        ResearchDisplayBusBridge bridge = new ResearchDisplayBusBridge(legacy);

        bridge.cleanup();

        assertEquals(1, legacy.cleanups, "插件 disable 时必须清掉直写 BossBar");
    }

    @Test
    void missingSinkIsTolerated() {
        ResearchDisplayBusBridge bridge = new ResearchDisplayBusBridge(null);

        assertDoesNotThrow(() -> {
            bridge.show(PLAYER, "SpawnTown", "title", 0.5);
            bridge.hide(PLAYER);
            bridge.cleanup();
        }, "没有 sink 时也不许抛（宁可什么都不显示，也不许把异常抛进事件线程）");
    }

    @Test
    void nullArgumentsAreStillRejected() {
        ResearchDisplayBusBridge bridge = new ResearchDisplayBusBridge(new RecordingLegacy());

        assertThrows(NullPointerException.class, () -> bridge.show(null, "t", "title", 0.5));
        assertThrows(NullPointerException.class, () -> bridge.show(PLAYER, null, "title", 0.5));
        assertThrows(NullPointerException.class, () -> bridge.show(PLAYER, "t", null, 0.5));
        assertThrows(NullPointerException.class, () -> bridge.hide(null));
    }

    @Test
    void hideIsIdempotent() {
        RecordingLegacy legacy = new RecordingLegacy();
        ResearchDisplayBusBridge bridge = new ResearchDisplayBusBridge(legacy);

        bridge.hide(PLAYER);
        bridge.hide(PLAYER);

        assertEquals(List.of(PLAYER, PLAYER), legacy.hides,
                "重复 hide 不抛（退服 / 离开地块 / disable 都可能连发）");
    }

    @Test
    void progressValueIsPassedThroughUnchanged() {
        RecordingLegacy legacy = new RecordingLegacy();
        ResearchDisplayBusBridge bridge = new ResearchDisplayBusBridge(legacy);

        bridge.show(PLAYER, "SpawnTown", "t", 0.0);
        bridge.show(PLAYER, "SpawnTown", "t", 1.0);
        bridge.show(PLAYER, "SpawnTown", "t", 2.5);

        assertEquals(List.of(0.0, 1.0, 2.5),
                legacy.shows.stream().map(RecordingLegacy.Show::progress).toList(),
                "bridge 只透传；数值钳制仍由直写 sink 负责（保持现有行为）");
    }

    @Test
    void constructingTheBridgeEmitsNothing() {
        RecordingLegacy legacy = new RecordingLegacy();

        new ResearchDisplayBusBridge(legacy);

        assertTrue(legacy.shows.isEmpty(), "构造 bridge 本身不得下发任何 BossBar");
        assertTrue(legacy.hides.isEmpty(), "构造 bridge 本身不得隐藏任何 BossBar");
        assertEquals(0, legacy.cleanups, "构造 bridge 本身不得做清理");
    }

    /**
     * 签名钉子：直写路径不再使用逻辑时间片，装配入口也不再查询任何服务。
     * {@code show} 的 {@code long nowTick} 与 {@code lookup} 的 {@code Logger logger}
     * 都是总线时代残留的死参数 —— 谁把它们加回来，这条会先在运行时变红。
     */
    @Test
    void bridgeSignatureCarriesNoDeadLegacyParameters() {
        List<String> offenders = new ArrayList<>();
        for (Method m : ResearchDisplayBusBridge.class.getDeclaredMethods()) {
            for (Class<?> p : m.getParameterTypes()) {
                if (p == long.class || p == Logger.class) {
                    offenders.add(m.getName() + " -> " + p.getSimpleName());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "直写路径没有任何逻辑时间片 / 服务查询：nowTick 与 logger 都是死参数，必须删掉。命中: " + offenders);
    }

    private static final class RecordingLegacy implements ResearchDisplayBusBridge.LegacyBossBarSink {
        record Show(UUID playerId, String town, String title, double progress) { }

        final List<Show> shows = new ArrayList<>();
        final List<UUID> hides = new ArrayList<>();
        int cleanups;

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
            cleanups++;
        }
    }
}
