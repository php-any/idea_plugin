package com.company.plugin.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.project.DumbService;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 启动初始化：确保 .idea/index 目录存在，并触发索引预热
 */
public class ZyIndexStartup implements StartupActivity.DumbAware {
    private static final Logger LOG = Logger.getInstance(ZyIndexStartup.class);

    @Override
    public void runActivity(@NotNull Project project) {
        try {
            String basePath = project.getBasePath();
            if (basePath != null) {
                Path indexDir = Path.of(basePath, ".idea", "index");
                if (Files.notExists(indexDir)) {
                    Files.createDirectories(indexDir);
                    // 刷新 VFS，确保 IDE 看到新目录
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(indexDir.toFile());
                    LOG.info("Created project index directory: " + indexDir);
                }
            }
        } catch (Exception e) {
            LOG.warn("Create index directory failed", e);
        }

        // 智能模式就绪后自动预热索引
        DumbService.getInstance(project).runWhenSmart(() -> {
            com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService().submit(() -> {
                try {
                    ZySymbolIndexService.getInstance(project).ensureUpToDate();
                } catch (Exception e) {
                    LOG.warn("Warm-up symbol index failed", e);
                }
            });
        });

        // 绑定 VFS 监听，文件变更后自动增量刷新（合并抖动）
        ZyVfsListener.attach(project);
    }
}


