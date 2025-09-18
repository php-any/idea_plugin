package com.company.plugin.index;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 符号索引服务（项目级）
 * - 负责建立和持久化类与函数的定义索引
 * - 使用文件修改时间戳增量校验，仅在目录/文件变化时重建
 * - 为导航与补全提供快速查询能力
 */
@State(name = "ZySymbolIndex", storages = @Storage("$PROJECT_CONFIG_DIR$/index/zySymbolIndex.xml"))
public class ZySymbolIndexService implements PersistentStateComponent<ZySymbolIndexService.State> {

    private static final Logger LOG = Logger.getInstance(ZySymbolIndexService.class);

    /**
     * 持久化状态
     * 保存符号到位置映射与文件时间戳表
     */
    public static class State {
        /** 符号名 -> 位置列表 */
        public Map<String, List<LocationState>> symbolToLocations = new HashMap<>();
        /** 文件路径 -> 最后修改时间戳 */
        public Map<String, Long> fileTimestamps = new HashMap<>();
        /** 上次完整扫描时间 */
        public long lastFullScanMs = 0L;
    }

    /**
     * 存储的位置信息
     */
    public static class LocationState {
        public String filePath;
        public int offset;

        public LocationState() {}

        public LocationState(String filePath, int offset) {
            this.filePath = filePath;
            this.offset = offset;
        }
    }

    private final Project project;
    private final Object lock = new Object();
    private State state = new State();
    // 内存查询缓存：符号名 -> 最近查询结果与时间
    private final Map<String, List<LocationState>> inMemoryCache = new HashMap<>();
    private long lastEnsureUpToDateMs = 0L;
    // ensureUpToDate 最小间隔（毫秒），避免高频重复扫描
    private static final long ENSURE_THROTTLE_MS = 1500L;

    // 正则：捕获函数/类定义名
    private static final Pattern FUNCTION_DEF = Pattern.compile("function\\s+(\\w+)\\s*\\(");
    private static final Pattern CLASS_DEF = Pattern.compile("class\\s+(\\w+)\\s*\\{");
    // 类属性模式：支持 "string $name;" 或 "$age;" 格式
    private static final Pattern PROPERTY_DEF = Pattern.compile("(?:\\w+\\s+)?\\$([a-zA-Z_][a-zA-Z0-9_]*)");

    public ZySymbolIndexService(Project project) {
        this.project = project;
    }

    /**
     * 获取服务实例
     */
    public static ZySymbolIndexService getInstance(@NotNull Project project) {
        return project.getService(ZySymbolIndexService.class);
    }

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    /**
     * 确保索引最新：若发现文件新增/删除/修改则重建
     */
    public void ensureUpToDate() {
        synchronized (lock) {
            try {
                long now = System.currentTimeMillis();
                if (now - lastEnsureUpToDateMs < ENSURE_THROTTLE_MS) {
                    return; // 节流，减少频繁触发
                }
                Map<String, Long> currentTimestamps = collectCurrentFileTimestamps();
                boolean changed = !isSameFilesAndTimestamps(currentTimestamps, state.fileTimestamps);
                if (changed) {
                    // 增量更新：仅对新增/修改/删除的文件更新索引
                    updateIndexIncremental(currentTimestamps);
                }
                // 构建/刷新目录级 JSON 索引
                LOG.info("ZySymbolIndex: building JSON indexes");
                ZyJsonIndexStore.buildAllDirIndexes(project);
                lastEnsureUpToDateMs = now;
            } catch (Exception e) {
                LOG.warn("ZySymbolIndex ensureUpToDate error", e);
            }
        }
    }

    /**
     * 增量更新索引：只处理变更的文件
     */
    private void updateIndexIncremental(Map<String, Long> currentTimestamps) {
        // 计算差异集合
        Set<String> oldPaths = new HashSet<>(state.fileTimestamps.keySet());
        Set<String> newPaths = new HashSet<>(currentTimestamps.keySet());

        List<String> removed = new ArrayList<>();
        for (String p : oldPaths) {
            if (!newPaths.contains(p)) removed.add(p);
        }

        List<String> addedOrModified = new ArrayList<>();
        for (String p : newPaths) {
            Long oldTs = state.fileTimestamps.get(p);
            Long newTs = currentTimestamps.get(p);
            if (oldTs == null || !oldTs.equals(newTs)) {
                addedOrModified.add(p);
            }
        }

        // 若改动过多，回退为全量重建
        int total = newPaths.size();
        if ((removed.size() + addedOrModified.size()) > Math.max(200, total * 0.3)) {
            LOG.info("ZySymbolIndex: too many changes (removed=" + removed.size() + ", changed=" + addedOrModified.size() + "), fallback to full rebuild");
            rebuildIndex(currentTimestamps);
            return;
        }

        if (!removed.isEmpty()) {
            LOG.info("ZySymbolIndex: removing from index files=" + removed.size());
        }
        for (String path : removed) {
            removeFileFromIndex(path);
            state.fileTimestamps.remove(path);
        }

        if (!addedOrModified.isEmpty()) {
            LOG.info("ZySymbolIndex: reindexing changed files=" + addedOrModified.size());
        }
        for (String path : addedOrModified) {
            reindexSingleFile(path);
            Long ts = currentTimestamps.get(path);
            if (ts != null) state.fileTimestamps.put(path, ts);
        }

        // 更新最后扫描时间并清理内存缓存
        state.lastFullScanMs = System.currentTimeMillis();
        inMemoryCache.clear();
    }

    /**
     * 从索引中移除某个文件的所有符号条目
     */
    private void removeFileFromIndex(@NotNull String filePath) {
        if (state.symbolToLocations.isEmpty()) return;
        for (Map.Entry<String, List<LocationState>> e : state.symbolToLocations.entrySet()) {
            List<LocationState> list = e.getValue();
            if (list == null || list.isEmpty()) continue;
            list.removeIf(ls -> filePath.equals(ls.filePath));
        }
        // 清理空条目
        state.symbolToLocations.entrySet().removeIf(en -> en.getValue() == null || en.getValue().isEmpty());
    }

    /**
     * 重新索引单个文件并写回到现有索引映射
     */
    private void reindexSingleFile(@NotNull String filePath) {
        try {
            VirtualFile vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath);
            if (vf == null || vf.isDirectory() || !vf.getName().endsWith(".zy")) return;

            String text = new String(vf.contentsToByteArray(), vf.getCharset());

            // 先移除旧条目
            removeFileFromIndex(filePath);

            // 收集该文件的新条目并合并到现有索引
            Map<String, List<LocationState>> tmp = new HashMap<>();
            indexFile(vf, text, tmp);
            for (Map.Entry<String, List<LocationState>> en : tmp.entrySet()) {
                for (LocationState ls : en.getValue()) {
                    add(state.symbolToLocations, en.getKey(), ls);
                }
            }
        } catch (Exception e) {
            LOG.warn("ZySymbolIndex: reindexSingleFile error for " + filePath, e);
        }
    }

    /**
     * 查询符号定义位置，若传入首选路径片段，则优先返回匹配该片段的条目
     * @param name 符号名（类名/函数名）
     * @param preferredPathSegment 首选路径片段（如 Model/Users）
     */
    public List<LocationState> findDefinitions(@NotNull String name, @Nullable String preferredPathSegment) {
        ensureUpToDate();
        // 先查内存缓存
        List<LocationState> cached = inMemoryCache.get(name);
        List<LocationState> list = cached != null ? cached : state.symbolToLocations.getOrDefault(name, Collections.emptyList());
        if (list.isEmpty() || preferredPathSegment == null || preferredPathSegment.isEmpty()) {
            inMemoryCache.put(name, list);
            return list;
        }
        String pref = ("/" + preferredPathSegment).replace('\\', '/').toLowerCase();
        List<LocationState> preferred = new ArrayList<>();
        List<LocationState> others = new ArrayList<>();
        for (LocationState ls : list) {
            String path = ls.filePath == null ? "" : ls.filePath.replace('\\', '/').toLowerCase();
            boolean match = path.endsWith(pref + ".zy") || path.contains(pref + "/") || path.contains("/" + pref);
            if (match) preferred.add(ls); else others.add(ls);
        }
        if (!preferred.isEmpty()) return preferred;
        inMemoryCache.put(name, list);
        return list;
    }

    /**
     * 重建索引：扫描所有 .zy 文件，提取类与函数定义
     */
    private void rebuildIndex(Map<String, Long> newTimestamps) {
        LOG.info("ZySymbolIndex: rebuilding index");
        Map<String, List<LocationState>> symbolMap = new HashMap<>();

        VirtualFile base = project.getBaseDir();
        if (base == null) {
            LOG.warn("ZySymbolIndex: project base dir is null");
            return;
        }

        // 使用更全面的文件搜索方式
        try {
            VfsUtilCore.visitChildrenRecursively(base, new VirtualFileVisitor<>() {
                @Override
                public boolean visitFile(@NotNull VirtualFile file) {
                    try {
                        // 检查是否被取消
                        com.intellij.openapi.progress.ProgressManager.checkCanceled();
                        
                        if (file.isDirectory()) return true;
                        if (!file.getName().endsWith(".zy")) return true;
                        
                        String text = new String(file.contentsToByteArray(), file.getCharset());
                        indexFile(file, text, symbolMap);
                        return true;
                    } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                        // 搜索被取消，停止遍历
                        return false;
                    } catch (Exception e) {
                        LOG.warn("ZySymbolIndex: read file error " + file.getPath(), e);
                        return true; // 继续处理其他文件
                    }
                }
            });
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            // 搜索被取消，使用已收集的符号
            LOG.debug("Index rebuild cancelled, using " + symbolMap.size() + " symbols collected so far");
        } catch (Exception e) {
            LOG.warn("Error during index rebuild", e);
        }

        state.symbolToLocations = symbolMap;
        state.fileTimestamps = newTimestamps;
        state.lastFullScanMs = System.currentTimeMillis();
        // 重建后清理内存缓存，防止陈旧数据
        inMemoryCache.clear();
        LOG.info("ZySymbolIndex: rebuild done. symbols=" + state.symbolToLocations.size());
        try {
            com.intellij.openapi.application.ApplicationManager.getApplication().saveSettings();
        } catch (Throwable ignore) {}
    }

    /**
     * 解析一个文件，提取符号并写入 symbolMap
     */
    private void indexFile(@NotNull VirtualFile file, @NotNull String text, @NotNull Map<String, List<LocationState>> symbolMap) {
        Matcher fm = FUNCTION_DEF.matcher(text);
        while (fm.find()) {
            String name = fm.group(1);
            int offset = fm.start(1);
            add(symbolMap, name, new LocationState(file.getPath(), offset));
        }
        Matcher cm = CLASS_DEF.matcher(text);
        while (cm.find()) {
            String name = cm.group(1);
            int offset = cm.start(1);
            add(symbolMap, name, new LocationState(file.getPath(), offset));
        }
    }

    private static void add(Map<String, List<LocationState>> map, String key, LocationState value) {
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    /**
     * 收集当前项目下所有 .zy 文件的时间戳
     */
    private Map<String, Long> collectCurrentFileTimestamps() {
        Map<String, Long> ts = new HashMap<>();
        VirtualFile base = project.getBaseDir();
        if (base == null) return ts;
        
        try {
            VfsUtilCore.visitChildrenRecursively(base, new VirtualFileVisitor<>() {
                @Override
                public boolean visitFile(@NotNull VirtualFile file) {
                    try {
                        // 检查是否被取消
                        com.intellij.openapi.progress.ProgressManager.checkCanceled();
                        
                        if (file.isDirectory()) return true;
                        if (file.getName().endsWith(".zy")) {
                            ts.put(file.getPath(), file.getTimeStamp());
                        }
                        return true;
                    } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                        // 搜索被取消，停止遍历
                        return false;
                    }
                }
            });
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            // 搜索被取消，返回已收集的时间戳
            LOG.debug("File timestamp collection cancelled, returning " + ts.size() + " timestamps");
        } catch (Exception e) {
            LOG.warn("Error collecting file timestamps", e);
        }
        
        return ts;
    }

    private static boolean isSameFilesAndTimestamps(Map<String, Long> a, Map<String, Long> b) {
        if (a.size() != b.size()) return false;
        for (Map.Entry<String, Long> e : a.entrySet()) {
            Long v = b.get(e.getKey());
            if (v == null || !v.equals(e.getValue())) return false;
        }
        return true;
    }

    /**
     * 供 JSON 构建复用：从文本中提取符号
     */
    public static List<ZyJsonIndexStore.SymbolEntry> extractSymbols(String text) {
        List<ZyJsonIndexStore.SymbolEntry> list = new ArrayList<>();
        if (text == null) return list;
        Matcher fm = FUNCTION_DEF.matcher(text);
        while (fm.find()) {
            ZyJsonIndexStore.SymbolEntry se = new ZyJsonIndexStore.SymbolEntry();
            se.kind = "function"; se.name = fm.group(1); se.offset = fm.start(1);
            list.add(se);
        }
        Matcher cm = CLASS_DEF.matcher(text);
        while (cm.find()) {
            ZyJsonIndexStore.SymbolEntry se = new ZyJsonIndexStore.SymbolEntry();
            se.kind = "class"; se.name = cm.group(1); se.offset = cm.start(1);
            list.add(se);
        }
        return list;
    }

    /**
     * 使用基于作用域的解析器提取符号
     */
    public static List<ZyJsonIndexStore.SymbolEntry> extractSymbolsWithNamespace(String text) {
        List<ZyJsonIndexStore.SymbolEntry> list = new ArrayList<>();
        if (text == null || text.isEmpty()) return list;
        
        try {
            // 使用新的基于作用域的解析器
            List<ZyScopeBasedParser.Symbol> symbols = ZyScopeBasedParser.extractSymbols(text);
            
            for (ZyScopeBasedParser.Symbol symbol : symbols) {
                ZyJsonIndexStore.SymbolEntry entry = new ZyJsonIndexStore.SymbolEntry();
                entry.kind = symbol.kind;
                entry.name = symbol.name;
                entry.offset = symbol.offset;
                entry.namespace = symbol.namespace;
                entry.fqn = symbol.fqn;
                list.add(entry);
            }
            
            // 如果解析器成功返回了符号，直接返回
            if (!list.isEmpty()) {
                return list;
            }
        } catch (Exception e) {
            // 如果新解析器失败，记录警告并回退到旧的正则方式
            LOG.warn("Scope-based parser failed, falling back to regex: " + e.getMessage());
        }
        
        // 回退到旧的正则表达式方式
        return extractSymbolsWithRegex(text);
    }
    
    /**
     * 旧的正则表达式解析方式（作为回退）
     */
    private static List<ZyJsonIndexStore.SymbolEntry> extractSymbolsWithRegex(String text) {
        List<ZyJsonIndexStore.SymbolEntry> list = new ArrayList<>();
        if (text == null) return list;
        
        // 命名空间声明: 更宽松匹配，大小写不敏感，容忍前后内容
        Pattern NS_DECL = Pattern.compile("(?is)\\bnamespace\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\\\[A-Za-z_][A-Za-z0-9_]*)*)\\s*;?", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        String ns = null;
        Matcher nm = NS_DECL.matcher(text);
        if (nm.find()) ns = nm.group(1);

        // 先收集类的范围 [startBrace, endBrace]，用于判断方法归属
        class Block { int start; int end; String name; }
        List<Block> classBlocks = new ArrayList<>();
        Matcher cm = CLASS_DEF.matcher(text);
        while (cm.find()) {
            String className = cm.group(1);
            int nameStart = cm.start(1);
            // 从 class 声明后的第一个 '{' 开始做简单括号匹配，找到对应 '}'
            int bracePos = text.indexOf('{', cm.end(1));
            if (bracePos < 0) bracePos = nameStart; // 兜底避免越界
            int depth = 0; int i = bracePos; int end = text.length();
            for (; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '{') depth++;
                else if (ch == '}') { depth--; if (depth == 0) { end = i; break; } }
            }
            Block b = new Block(); b.start = bracePos; b.end = end; b.name = className; classBlocks.add(b);

            ZyJsonIndexStore.SymbolEntry se = new ZyJsonIndexStore.SymbolEntry();
            se.kind = "class"; se.name = className; se.offset = nameStart;
            se.namespace = ns; se.fqn = ns != null ? ns + "\\" + className : className;
            list.add(se);
        }

        // 再收集函数，判断是否处于某个类块内 → 记为 method，并生成 fqn Namespace\Class::method
        Matcher fm = FUNCTION_DEF.matcher(text);
        while (fm.find()) {
            String func = fm.group(1);
            int off = fm.start(1);
            String kind = "function";
            String fqn = (ns != null ? ns + "\\" : "") + func;
            for (Block b : classBlocks) {
                if (off > b.start && off < b.end) {
                    kind = "method";
                    fqn = (ns != null ? ns + "\\" : "") + b.name + "::" + func;
                    break;
                }
            }
            ZyJsonIndexStore.SymbolEntry se = new ZyJsonIndexStore.SymbolEntry();
            se.kind = kind; se.name = func; se.offset = off; se.namespace = ns; se.fqn = fqn;
            list.add(se);
        }

        // 收集类属性，判断是否处于某个类块内但不在方法内 → 记为 property，并生成 fqn Namespace\Class::$property
        Matcher pm = PROPERTY_DEF.matcher(text);
        while (pm.find()) {
            String prop = pm.group(1);
            int off = pm.start(1);
            String kind = "property";
            String fqn = (ns != null ? ns + "\\" : "") + prop;
            
            // 检查是否在类块内但不在方法内
            for (Block b : classBlocks) {
                if (off > b.start && off < b.end) {
                    // 检查是否在方法内：查找该位置之前最近的 function 关键字
                    boolean inMethod = false;
                    String beforeText = text.substring(b.start, off);
                    Matcher methodMatcher = FUNCTION_DEF.matcher(beforeText);
                    int lastMethodStart = -1;
                    while (methodMatcher.find()) {
                        lastMethodStart = methodMatcher.start();
                    }
                    
                    if (lastMethodStart >= 0) {
                        // 找到最近的 function，检查该 function 的结束位置
                        int methodStart = b.start + lastMethodStart;
                        int methodBracePos = text.indexOf('{', methodStart);
                        if (methodBracePos > 0 && methodBracePos < off) {
                            // 简单括号匹配找到方法结束位置
                            int depth = 0;
                            for (int i = methodBracePos; i < off; i++) {
                                char ch = text.charAt(i);
                                if (ch == '{') depth++;
                                else if (ch == '}') depth--;
                            }
                            inMethod = depth > 0; // 如果括号深度>0，说明还在方法内
                        }
                    }
                    
                    if (!inMethod) {
                        kind = "property";
                        fqn = (ns != null ? ns + "\\" : "") + b.name + "::$" + prop;
                        break;
                    }
                }
            }
            
            // 只有确认是类属性时才添加
            if ("property".equals(kind) && fqn.contains("::$")) {
                ZyJsonIndexStore.SymbolEntry se = new ZyJsonIndexStore.SymbolEntry();
                se.kind = kind; se.name = "$" + prop; se.offset = off; se.namespace = ns; se.fqn = fqn;
                list.add(se);
            }
        }
        return list;
    }
}


