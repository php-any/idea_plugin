package com.company.plugin.navigation;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * ZY 简单导航动作
 * 用于测试跳转功能
 */
public class ZySimpleNavigationAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(ZySimpleNavigationAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        
        if (project == null || editor == null || file == null) {
            Messages.showInfoMessage(project, "无法获取编辑器或文件信息", "ZY 导航测试");
            return;
        }
        
        // 只处理 .zy 文件
        if (!file.getName().endsWith(".zy")) {
            Messages.showInfoMessage(project, "此功能仅支持 .zy 文件", "ZY 导航测试");
            return;
        }
        
        // 获取当前光标位置的元素
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        
        if (element != null) {
            String text = element.getText();
            String elementType = element.getClass().getSimpleName();
            int textOffset = element.getTextOffset();
            int textLength = element.getTextLength();
            
            LOG.info("Navigation test - Element: " + elementType + 
                    ", Text: '" + text + "'" +
                    ", Offset: " + textOffset + 
                    ", Length: " + textLength);
            
            // 尝试查找定义
            String word = text != null ? text.trim() : "";
            if (!word.isEmpty() && word.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                // 在文件中查找定义
                String fileText = file.getText();
                if (fileText != null) {
                    // 查找函数定义
                    String functionPattern = "function\\s+" + word + "\\s*\\(";
                    if (fileText.matches(".*" + functionPattern + ".*")) {
                        int functionIndex = fileText.indexOf("function " + word);
                        if (functionIndex >= 0) {
                            Messages.showInfoMessage(project, 
                                "找到函数定义: " + word + "\n" +
                                "位置: " + functionIndex + "\n" +
                                "元素类型: " + elementType, 
                                "ZY 导航测试");
                            return;
                        }
                    }
                    
                    // 查找类定义
                    String classPattern = "class\\s+" + word + "\\s*[\\{]";
                    if (fileText.matches(".*" + classPattern + ".*")) {
                        int classIndex = fileText.indexOf("class " + word);
                        if (classIndex >= 0) {
                            Messages.showInfoMessage(project, 
                                "找到类定义: " + word + "\n" +
                                "位置: " + classIndex + "\n" +
                                "元素类型: " + elementType, 
                                "ZY 导航测试");
                            return;
                        }
                    }
                    
                    // 查找变量定义
                    String varPattern = "(var|let|const)\\s+" + word + "\\s*[=;]";
                    if (fileText.matches(".*" + varPattern + ".*")) {
                        int varIndex = fileText.indexOf(word);
                        if (varIndex >= 0) {
                            Messages.showInfoMessage(project, 
                                "找到变量定义: " + word + "\n" +
                                "位置: " + varIndex + "\n" +
                                "元素类型: " + elementType, 
                                "ZY 导航测试");
                            return;
                        }
                    }
                }
            }
            
            Messages.showInfoMessage(project, 
                "当前元素: " + elementType + "\n" +
                "文本: '" + text + "'\n" +
                "偏移: " + textOffset + "\n" +
                "长度: " + textLength + "\n" +
                "未找到定义", 
                "ZY 导航测试");
        } else {
            Messages.showInfoMessage(project, "未找到代码元素", "ZY 导航测试");
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
