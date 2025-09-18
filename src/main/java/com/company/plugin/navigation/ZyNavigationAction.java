package com.company.plugin.navigation;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * ZY 导航动作
 * 提供基本的 ZY 文件导航功能
 */
public class ZyNavigationAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        
        if (project == null || editor == null || file == null) {
            return;
        }
        
        // 只处理 .zy 文件
        if (!file.getName().endsWith(".zy")) {
            Messages.showInfoMessage(project, "此功能仅支持 .zy 文件", "ZY 导航");
            return;
        }
        
        // 获取当前光标位置的元素
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        
        if (element != null) {
            String text = element.getText();
            if (text != null && !text.trim().isEmpty()) {
                Messages.showInfoMessage(project, 
                    "当前元素: " + text + "\n" +
                    "类型: " + element.getClass().getSimpleName() + "\n" +
                    "位置: " + element.getTextRange(), 
                    "ZY 导航信息");
            } else {
                Messages.showInfoMessage(project, "未找到有效的代码元素", "ZY 导航");
            }
        } else {
            Messages.showInfoMessage(project, "未找到代码元素", "ZY 导航");
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        
        // 只在有项目且是 .zy 文件时启用
        boolean enabled = project != null && file != null && file.getName().endsWith(".zy");
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}
