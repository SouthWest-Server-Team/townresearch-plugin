package com.xinantown.townresearch.display;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构性契约测试（案 1 收口）：research 的 BossBar 只走自己的直写实现，跨仓契约不再被使用。
 *
 * <p>为什么每条都针对生产源码断言：这些不变量没有可纯 JVM 构造的运行时入口
 * （Bukkit 未启动时 {@code Bukkit.createBossBar} 直接抛、{@code Bukkit.getPlayer} 返回 null），
 * 所以按工作区既有写法（{@code Easter-Egg-plugin/PluginWiringContractTest}）读源码、去掉注释后判据。
 *
 * <p>为什么必须删干净 {@code com.xinantown.display}：① 运行期该公开面的 BossBar 方法与
 * {@code core.BossBarRequest} 都已删除（真服日志：
 * {@code NoSuchMethodError: 'void ...DisplayBusService.clearSource(UUID, String)' at ResearchDisplayBusBridge.hide}），
 * 而 {@code NoSuchMethodError}/{@code NoClassDefFoundError} 都是 {@link Error}，
 * {@code catch (Exception)} 接不住 ⇒ 每次退服 / 插件 disable 都会把堆栈抛进事件线程；
 * ② 编译期依赖的那份 display jar 里旧类只是「还没被清掉」，一换 jar 整仓就编译失败。
 */
class ResearchBossBarDirectWriteContractTest {

    private static final Path MAIN_ROOT = Path.of("src", "main", "java");

    /** Source with block and line comments removed（解释旧行为的注释不算命中）。 */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    /** Main-source hits for {@code marker}, optionally restricted to a set of file names. */
    private static List<String> occurrences(String marker, Set<String> onlyFileNames) throws IOException {
        assertTrue(Files.isDirectory(MAIN_ROOT),
                "测试工作目录必须是模块根（找不到 " + MAIN_ROOT.toAbsolutePath() + "）");
        List<String> hits = new ArrayList<>();
        try (var files = Files.walk(MAIN_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                if (!onlyFileNames.isEmpty() && !onlyFileNames.contains(file.getFileName().toString())) {
                    continue;
                }
                String code = codeOnly(Files.readString(file, StandardCharsets.UTF_8));
                if (code.contains(marker)) {
                    hits.add(file.getFileName() + " -> " + marker);
                }
            }
        }
        return hits;
    }

    private static String readMain(String fileName) throws IOException {
        Path file = MAIN_ROOT.resolve(
                "com/xinantown/townresearch/" + fileName);
        assertTrue(Files.exists(file), "找不到被测源码: " + file.toAbsolutePath());
        return codeOnly(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Body of the first method whose signature contains {@code signatureFragment}, braces balanced. */
    private static String methodBody(String code, String signatureFragment) {
        int start = code.indexOf(signatureFragment);
        assertTrue(start >= 0, "源码里找不到: " + signatureFragment);
        int open = code.indexOf('{', start);
        assertTrue(open > 0, "找不到方法体起始: " + signatureFragment);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return code.substring(open, i + 1);
                }
            }
        }
        throw new IllegalStateException("花括号不平衡: " + signatureFragment);
    }

    // ---------------------------------------------------------------- 跨仓契约：display 已删的 BossBar 公开面

    @Test
    void mainSourcesNoLongerReferenceTheDeletedDisplayBusBossBarApi() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String marker : new String[] {
                "com.xinantown.display", "requestBossBar", "clearSource", "BossBarRequest"}) {
            offenders.addAll(occurrences(marker, Set.of()));
        }
        assertTrue(offenders.isEmpty(),
                "display 的 BossBar 公开面（requestBossBar / clearSource / core.BossBarRequest）已在 2026-09-23 随旧总线删除："
                        + "调用点就是 NoSuchMethodError / NoClassDefFoundError 的源头，且它们都是 Error，catch (Exception) 接不住，"
                        + "会在玩家退服（PlayerQuitEvent → hide）与插件 disable 时抛进事件线程。"
                        + "research 必须只走自己的直写 BossBar。命中: " + offenders);
    }

    // ---------------------------------------------------------------- 红线①：研究进度不许进 ActionBar

    @Test
    void researchProgressNeverGoesThroughTheActionBarChannel() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String marker : new String[] {
                "ActionBarRequest", "requestActionBar", "ActionBarView", "defaultAnimation"}) {
            offenders.addAll(occurrences(marker, Set.of()));
        }
        assertTrue(offenders.isEmpty(),
                "红线①：研究进度只许走 BossBar，不许进 ActionBar（ActionBar 是轮换/城邦行的通道）。命中: " + offenders);
    }

    // ---------------------------------------------------------------- 红线②：可见性不做居民判定

    @Test
    void bossBarVisibilityStillNeverAsksForTownResidency() throws IOException {
        Set<String> bossBarSources = Set.of("ResearchBossBarManager.java", "ResearchBossBarVisibility.java");
        List<String> offenders = new ArrayList<>();
        for (String marker : new String[] {"isResident(", "getTown(", "getTownOrNull(", "hasTown("}) {
            offenders.addAll(occurrences(marker, bossBarSources));
        }
        assertTrue(offenders.isEmpty(),
                "红线②：可见性判据 = 玩家站在研究所 TownBlock（TownyAPI.getTownBlock + matchesAnyLab）且该镇有活跃研究，"
                        + "不许改成「是本镇居民」。命中: " + offenders);
    }

    @Test
    void aVisitorStandingOnTheLabPlotIsStillAWatcher() {
        UUID visitor = UUID.fromString("33333333-3333-3333-3333-333333333333");
        assertEquals(Set.of(visitor),
                ResearchBossBarVisibility.selectNearLabPlayerIds(
                        List.of(new ResearchBossBarVisibility.OnlinePresence(visitor, true))),
                "红线②：非居民站在研究所地块上同样要看到进度条（访客 / 无城邦玩家）");
    }

    // ---------------------------------------------------------------- 结构：类加载不再触碰死掉的 display 类

    @Test
    void theBridgeClassLoadsWithoutResolvingTheDeadDisplayApi() {
        assertDoesNotThrow(() -> {
            Class<?> bridge = Class.forName("com.xinantown.townresearch.display.ResearchDisplayBusBridge");
            bridge.getDeclaredConstructors();
            bridge.getDeclaredMethods();
            bridge.getDeclaredFields();
        }, "bridge 的构造器 / 方法 / 字段签名都不许再引用 display 的类型：一旦引用，"
                + "NoClassDefFoundError（Error）会在事件线程上炸出来");
    }

    // ---------------------------------------------------------------- 结构性：退服 / disable 仍然走 hide

    @Test
    void playerQuitStillReachesTheHideCallOnTheBridge() throws IOException {
        String manager = readMain("ResearchBossBarManager.java");

        String onQuit = methodBody(manager, "public void onQuit(PlayerQuitEvent event)");
        assertTrue(onQuit.contains("hidePlayer("),
                "退服必须仍然隐藏进度条（现有行为不许丢）: " + onQuit);

        String hidePlayer = methodBody(manager, "private void hidePlayer(UUID playerId)");
        assertTrue(hidePlayer.contains("bridge.hide("),
                "hidePlayer 必须落到 bridge.hide(...)，这是事件线程到 BossBar 的唯一通路: " + hidePlayer);
    }
}
