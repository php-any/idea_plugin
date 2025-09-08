package com.company.plugin.language

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.lang.Language
import com.intellij.psi.TokenType

/**
 * ZY 词法记号定义
 * 定义基础的标识符、空白和其他类型的 Token，供词法分析和 PSI 构建使用
 */
object ZyTokenTypes {
    /** 文件根节点类型 */
    val FILE: IFileElementType = IFileElementType(ZyLanguage)

    /** 标识符 Token（字母/数字/下划线组成） */
    val IDENTIFIER: IElementType = object : IElementType("ZY_IDENTIFIER", ZyLanguage) {}

    /** 其他字符 Token（非标识符且非空白） */
    val OTHER: IElementType = object : IElementType("ZY_OTHER", ZyLanguage) {}

    /** 空白 Token（交给平台处理） */
    val WHITE_SPACE = TokenType.WHITE_SPACE
}


