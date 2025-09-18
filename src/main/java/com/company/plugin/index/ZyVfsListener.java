package com.company.plugin.index;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * VFS 监听：合并 .zy 变更，延迟刷新索引
 */
public class ZyVfsListener implements BulkFileListener {
    private static final Logger LOG = Logger.getInstance(ZyVfsListener.class);
    private final Project project;
    private final Alarm alarm;

    private ZyVfsListener(Project project) {
        this.project = project;
        this.alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
    }

    public static void attach(Project project) {
        var bus = project.getMessageBus();
        bus.connect().subscribe(com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES, new ZyVfsListener(project));
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        boolean touched = events.stream().anyMatch(e -> {
            var f = e.getFile();
            return f != null && !f.isDirectory() && f.getName().endsWith(".zy");
        });
        if (!touched) return;
        // 500ms 防抖
        alarm.cancelAllRequests();
        alarm.addRequest(() -> {
            try {
                ZySymbolIndexService.getInstance(project).ensureUpToDate();
            } catch (Exception e) {
                LOG.warn("VFS refresh failed", e);
            }
        }, 500);
    }
}


