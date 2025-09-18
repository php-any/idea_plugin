package com.company.plugin.index;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 索引读写器
 * 在 .idea/index/zy/<relDir>/index.json 下为每个目录生成一个缓存文件
 */
public final class ZyJsonIndexStore {
    private static final Logger LOG = Logger.getInstance(ZyJsonIndexStore.class);
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private ZyJsonIndexStore() {}

    public static Path getIndexRoot(@NotNull Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) return Path.of(".");
        return Path.of(basePath, ".idea", "index", "zy");
    }

    public static Path getDirIndexPath(@NotNull Project project, @NotNull String relativeDir) {
        Path root = getIndexRoot(project);
        if (relativeDir.isEmpty() || ".".equals(relativeDir)) return root.resolve("index.json");
        // 目录同级生成 index.json（非下级 zy/relativeDir/index.json）
        // 规范：.idea/index/zy/<relativeDir>.index.json
        String fileName = relativeDir.replace('/', '_').replace('\\', '_') + ".index.json";
        return root.resolve(fileName);
    }

    public static Path getNamespaceIndexPath(@NotNull Project project, @NotNull String namespace) {
        Path root = getIndexRoot(project);
        String fileName = "ns_" + namespace.replace('\\', '_').replace('/', '_') + ".index.json";
        return root.resolve(fileName);
    }

    public static String getRelativeDir(@NotNull Project project, @NotNull VirtualFile dir) {
        VirtualFile base = project.getBaseDir();
        if (base == null) return "";
        String basePath = base.getPath();
        String full = dir.getPath();
        if (full.equals(basePath)) return "";
        if (full.startsWith(basePath + "/")) return full.substring(basePath.length() + 1);
        return full; // fallback
    }

    public static void ensureDirExists(@NotNull Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
    }

    /**
     * 扫描指定目录（不递归）生成目录级 index.json
     */
    public static void buildDirIndex(@NotNull Project project, @NotNull VirtualFile dir) {
        try {
            if (!dir.isDirectory()) return;
            String rel = getRelativeDir(project, dir);
            LOG.info("Building index for directory: " + dir.getPath() + " (rel: " + rel + ")");
            
            // 先尝试从目录内任意 .zy 文件推断目录命名空间
            DirIndex index = new DirIndex();
            index.version = 1;
            index.dir = rel.replace('\\', '/');
            index.generatedAt = System.currentTimeMillis();
            index.files = new ArrayList<>();

            // 先尝试从目录内任意 .zy 文件推断目录命名空间
            String dirNamespace = null;
            for (VirtualFile child : dir.getChildren()) {
                if (!child.isDirectory() && child.getName().endsWith(".zy")) {
                    try {
                        String text = new String(child.contentsToByteArray(), child.getCharset());
                        var syms = ZySymbolIndexService.extractSymbolsWithNamespace(text);
                        for (var s : syms) { 
                            if (s.namespace != null && !s.namespace.isEmpty()) { 
                                dirNamespace = s.namespace; 
                                LOG.info("Found namespace in " + child.getName() + ": " + dirNamespace);
                                break; 
                            } 
                        }
                        if (dirNamespace != null && !dirNamespace.isEmpty()) break;
                    } catch (Exception ex) {
                        LOG.warn("Error reading file for namespace detection: " + child.getPath(), ex);
                    }
                }
            }

            for (VirtualFile child : dir.getChildren()) {
                try {
                    // 检查是否被取消
                    com.intellij.openapi.progress.ProgressManager.checkCanceled();
                    
                    if (child.isDirectory()) continue;
                    if (!child.getName().endsWith(".zy")) continue;
                    
                    String text = new String(child.contentsToByteArray(), child.getCharset());
                    FileEntry fe = new FileEntry();
                    fe.path = getRelativePath(project, child);
                    fe.mtime = child.getTimeStamp();
                    fe.size = child.getLength();
                    fe.symbols = ZySymbolIndexService.extractSymbolsWithNamespace(text);
                    if (fe.symbols != null && dirNamespace != null && !dirNamespace.isEmpty()) {
                        for (var s : fe.symbols) {
                            if (s.namespace == null || s.namespace.isEmpty()) {
                                s.namespace = dirNamespace;
                                s.fqn = dirNamespace + "\\" + s.name;
                            }
                        }
                    }
                    index.files.add(fe);
                    LOG.info("Added file to index: " + fe.path + " with " + (fe.symbols != null ? fe.symbols.size() : 0) + " symbols");
                } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                    // 搜索被取消，停止处理
                    LOG.debug("File processing cancelled");
                    break;
                } catch (Exception ex) {
                    LOG.warn("Read file failed: " + child.getPath(), ex);
                }
            }

            index.summary = new Summary();
            index.summary.fileCount = index.files.size();
            int symCount = 0;
            for (FileEntry f : index.files) symCount += (f.symbols == null ? 0 : f.symbols.size());
            index.summary.symbolCount = symCount;

            // 原子落盘
            Path out = (dirNamespace != null && !dirNamespace.isEmpty())
                    ? getNamespaceIndexPath(project, dirNamespace)
                    : getDirIndexPath(project, rel);
            LOG.info("Writing index to: " + out.toString() + " (namespace: " + dirNamespace + ")");
            ensureDirExists(out);
            Path tmp = out.resolveSibling("index.json.tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(index, w);
            }
            java.nio.file.Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            LOG.info("Successfully built index for directory: " + rel + " with " + index.files.size() + " files and " + symCount + " symbols");
        } catch (Exception e) {
            LOG.warn("Build dir index failed: " + dir.getPath(), e);
        }
    }

    public static DirIndex readDirIndex(@NotNull Project project, @NotNull String relativeDir) {
        Path p = getDirIndexPath(project, relativeDir);
        if (!Files.exists(p)) return null;
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, DirIndex.class);
        } catch (Exception e) {
            LOG.debug("Read dir index failed: " + p, e);
            return null;
        }
    }

    public static DirIndex readNamespaceIndex(@NotNull Project project, @NotNull String namespace) {
        Path p = getNamespaceIndexPath(project, namespace);
        if (!Files.exists(p)) return null;
        try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, DirIndex.class);
        } catch (Exception e) {
            LOG.debug("Read namespace index failed: " + p, e);
            return null;
        }
    }

    public static void buildAllDirIndexes(@NotNull Project project) {
        VirtualFile base = project.getBaseDir();
        if (base == null) return;
        
        LOG.info("Building all directory indexes starting from: " + base.getPath());
        
        try {
            // 广度优先：为每个包含 .zy 文件的目录生成 index.json
            VfsUtilCore.visitChildrenRecursively(base, new VirtualFileVisitor<>() {
                @Override
                public boolean visitFile(@NotNull VirtualFile file) {
                    try {
                        // 检查是否被取消
                        com.intellij.openapi.progress.ProgressManager.checkCanceled();
                        
                        if (file.isDirectory()) {
                            boolean hasZy = false;
                            for (VirtualFile c : file.getChildren()) {
                                if (!c.isDirectory() && c.getName().endsWith(".zy")) { 
                                    hasZy = true; 
                                    break; 
                                }
                            }
                            if (hasZy) {
                                LOG.info("Building index for directory: " + file.getPath());
                                buildDirIndex(project, file);
                            }
                        }
                        return true;
                    } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
                        // 搜索被取消，停止遍历
                        LOG.debug("Directory index building cancelled");
                        return false;
                    } catch (Exception e) {
                        LOG.warn("Error processing directory: " + file.getPath(), e);
                        return true; // 继续处理其他目录
                    }
                }
            });
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            // 搜索被取消，记录日志但不抛出异常
            LOG.debug("Directory index building cancelled");
            return;
        } catch (Exception e) {
            LOG.warn("Error building directory indexes", e);
        }
        
        // 同时生成根级 index.json（根目录下 .zy）
        try {
            LOG.info("Building index for root directory: " + base.getPath());
            buildDirIndex(project, base);
        } catch (Exception e) {
            LOG.warn("Error building root directory index", e);
        }
    }

    private static String getRelativePath(@NotNull Project project, @NotNull VirtualFile file) {
        VirtualFile base = project.getBaseDir();
        if (base == null) return file.getPath();
        String bp = base.getPath();
        String fp = file.getPath();
        if (fp.equals(bp)) return "";
        if (fp.startsWith(bp + "/")) return fp.substring(bp.length() + 1);
        return fp;
    }

    // JSON 模型
    public static class DirIndex {
        public int version;
        public String dir;
        public long generatedAt;
        public Summary summary;
        public List<FileEntry> files;
    }
    public static class Summary { public int fileCount; public int symbolCount; }
    public static class FileEntry {
        public String path; public long mtime; public long size; public List<SymbolEntry> symbols;
    }
    public static class SymbolEntry {
        public String kind; public String name; public int offset; public String namespace; public String fqn;
    }
}


