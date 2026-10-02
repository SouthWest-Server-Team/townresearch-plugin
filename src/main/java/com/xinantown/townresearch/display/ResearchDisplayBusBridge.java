package com.xinantown.townresearch.display;

import java.util.Objects;
import java.util.UUID;

/**
 * 研究 BossBar 的唯一出口：直写 Bukkit BossBar（{@link LegacyBossBarSink}）。
 *
 * <p>为什么不再借 display（案 1，用户 2026-09-27 拍板）：display 的公开面
 * （其 {@code api} 包下的 DisplayBusService）在 2026-09-23 随旧总线一起删掉了 BossBar 能力，
 * 只剩 ActionBar 的三个方法；本插件当年「拿得到服务就只走总线」，
 * 于是玩家退服 / 插件 disable 时会从事件线程抛出
 * {@code NoSuchMethodError}（旧总线方法）或 {@code NoClassDefFoundError}（旧总线请求类）。
 * 两者都是 {@link Error}，{@code catch (Exception)} 接不住 ⇒ 每次退服刷一次堆栈（真服日志已验证）。
 *
 * <p>同时 display 现行实现**完全不接管 BOSS_BAR 包**（其 packetevents 监听器与主类都写明
 * 「BossBar 保持现状 / 不再拥有任何 BossBar」）⇒ 直写既不会被拦、也不会被 cancel，
 * 旧注释里「双写会被 PacketEvents 当外部包 cancel」的前提已经失效。
 *
 * <p>红线：研究进度只走 BossBar，**不许**进 ActionBar；本类不得再出现任何
 * {@code com.xinantown.display} 引用（由 {@code ResearchBossBarDirectWriteContractTest} 钉住）。
 *
 * <p>命名说明：类名、{@link LegacyBossBarSink} 与 {@link #lookup} 都来自旧总线时代。
 * 现已不再查询任何服务；保留名字是为了不扩大改动面（改名属独立决定）。
 */
public final class ResearchDisplayBusBridge {

    /**
     * 直写实现（由 {@code ResearchBossBarManager} 提供）：{@code Bukkit.createBossBar} 那一条路。
     */
    public interface LegacyBossBarSink {
        void show(UUID playerId, String townName, String title, double progress);

        void hide(UUID playerId);

        void cleanupLegacy();
    }

    private final LegacyBossBarSink legacy;

    public ResearchDisplayBusBridge(LegacyBossBarSink legacy) {
        this.legacy = legacy;
    }

    /**
     * 装配入口。不再查询任何服务：不需要服务也能工作，
     * 也就没有任何「服务在、方法没了」的窗口。
     * 旧签名里的 {@code Logger} 在 warning 分支删除后已无用途，一并移除。
     */
    public static ResearchDisplayBusBridge lookup(LegacyBossBarSink legacy) {
        return new ResearchDisplayBusBridge(legacy);
    }

    /**
     * 下发直写 BossBar。直写路径不使用逻辑时间片，故不再接收 {@code nowTick}。
     */
    public void show(UUID playerId, String townName, String title, double progress) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(townName, "townName");
        Objects.requireNonNull(title, "title");
        if (legacy != null) {
            legacy.show(playerId, townName, title, progress);
        }
    }

    public void hide(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (legacy != null) {
            legacy.hide(playerId);
        }
    }

    public void cleanup() {
        if (legacy != null) {
            legacy.cleanupLegacy();
        }
    }
}
