package com.company.plugin.language;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.TokenType;

/**
 * ZY 词法记号定义
 * 定义基础的标识符、空白和其他类型的 Token，供词法分析和 PSI 构建使用
 */
public class ZyTokenTypes {
    /** 文件根节点类型 */
    public static final IFileElementType FILE = new IFileElementType(ZyLanguage.INSTANCE);

    /** 标识符 Token（字母/数字/下划线组成） */
    public static final IElementType IDENTIFIER = new IElementType("ZY_IDENTIFIER", ZyLanguage.INSTANCE) {};

    /** 其他字符 Token（非标识符且非空白） */
    public static final IElementType OTHER = new IElementType("ZY_OTHER", ZyLanguage.INSTANCE) {};

    /** 空白 Token（交给平台处理） */
    public static final IElementType WHITE_SPACE = TokenType.WHITE_SPACE;
}
