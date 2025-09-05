package com.company.plugin.language

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * ZY 文件类型定义
 * 基于 TextMate 语法文件 origami.tmLanguage.json
 * 支持 .zy 文件扩展名的语法高亮和代码提示
 */
class ZyFileType : LanguageFileType(ZyLanguage.INSTANCE) {
    
    companion object {
        // ZY 文件类型的单例实例
        val INSTANCE = ZyFileType()
    }
    
    /**
     * 获取文件类型名称
     * @return 文件类型名称 "ZY File"
     */
    override fun getName(): String = "ZY File"
    
    /**
     * 获取文件类型描述
     * @return 文件类型描述 "ZY Language File"
     */
    override fun getDescription(): @NlsContexts.Label String = "ZY Language File"
    
    /**
     * 获取默认文件扩展名
     * @return 默认扩展名 "zy"
     */
    override fun getDefaultExtension(): @NlsSafe String = "zy"
    
    /**
     * 获取文件图标
     * @return 文件图标，当前为 null，将在后续添加图标
     */
    override fun getIcon(): Icon? {
        // 返回内置图标 resources/icons/zy-file.svg
        return IconLoader.getIcon("/icons/zy-file.svg", ZyFileType::class.java)
    }
    
    /**
     * 获取文件字符编码
     * @param file 虚拟文件对象
     * @param content 文件内容字节数组
     * @return 字符编码，默认为 UTF-8
     */
    override fun getCharset(file: com.intellij.openapi.vfs.VirtualFile, content: ByteArray): @NlsSafe String {
        return "UTF-8"
    }
}
