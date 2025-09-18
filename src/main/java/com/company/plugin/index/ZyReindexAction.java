package com.company.plugin.index;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 手动触发 ZY 符号索引重建
 */
public class ZyReindexAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        long start = System.currentTimeMillis();
        ZySymbolIndexService.getInstance(project).ensureUpToDate();
        long cost = System.currentTimeMillis() - start;
        Notifications.Bus.notify(new Notification("ZY", "ZY Index", "Rebuilt in " + cost + " ms", NotificationType.INFORMATION), project);
    }
}


