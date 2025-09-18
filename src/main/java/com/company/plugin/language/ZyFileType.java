package com.company.plugin.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * ZY 文件类型定义
 * 基于 TextMate 语法文件 origami.tmLanguage.json
 * 支持 .zy 文件扩展名的语法高亮和代码提示
 */
public class ZyFileType extends LanguageFileType {
    
    /**
     * ZY 文件类型的单例实例
     */
    public static final ZyFileType INSTANCE = new ZyFileType();
    
    /**
     * 构造函数
     * 创建 ZY 文件类型实例
     */
    private ZyFileType() {
        super(ZyLanguage.INSTANCE);
    }
    
    /**
     * 获取文件类型名称
     * @return 文件类型名称 "ZY File"
     */
    @Override
    @NotNull
    public String getName() {
        return "ZY File";
    }
    
    /**
     * 获取文件类型描述
     * @return 文件类型描述 "ZY Language File"
     */
    @Override
    @NotNull
    public String getDescription() {
        return "ZY Language File";
    }
    
    /**
     * 获取默认文件扩展名
     * @return 默认扩展名 "zy"
     */
    @Override
    @NotNull
    public String getDefaultExtension() {
        return "zy";
    }
    
    /**
     * 获取文件图标
     * @return 文件图标，当前为 null，将在后续添加图标
     */
    @Override
    @Nullable
    public Icon getIcon() {
        // 返回内置图标 resources/icons/zy-file.svg
        return IconLoader.getIcon("/icons/zy-file.svg", ZyFileType.class);
    }
    
    /**
     * 获取文件字符编码
     * @param file 虚拟文件对象
     * @param content 文件内容字节数组
     * @return 字符编码，默认为 UTF-8
     */
    @Override
    @NotNull
    public String getCharset(@NotNull com.intellij.openapi.vfs.VirtualFile file, @NotNull byte[] content) {
        return "UTF-8";
    }
}
