package com.company.plugin.highlighting;

import com.company.plugin.language.ZyLanguage;
import com.intellij.psi.tree.IElementType;

/**
 * ZY 语言标记类型
 * 基于 TextMate 语法文件 origami.tmLanguage.json
 * 支持 ZY 语言和 PHP 关键字
 */
public class ZyTokenTypes {
    
    // 通用标记类型
    public static final IElementType KEYWORD = new IElementType("ZY_KEYWORD", ZyLanguage.INSTANCE);
    public static final IElementType STRING = new IElementType("ZY_STRING", ZyLanguage.INSTANCE);
    public static final IElementType NUMBER = new IElementType("ZY_NUMBER", ZyLanguage.INSTANCE);
    public static final IElementType COMMENT = new IElementType("ZY_COMMENT", ZyLanguage.INSTANCE);
    public static final IElementType IDENTIFIER = new IElementType("ZY_IDENTIFIER", ZyLanguage.INSTANCE);
    public static final IElementType OPERATOR = new IElementType("ZY_OPERATOR", ZyLanguage.INSTANCE);
    public static final IElementType FILE = new IElementType("ZY_FILE", ZyLanguage.INSTANCE);
    
    // PHP 特定标记类型
    public static final IElementType PHP_KEYWORD = new IElementType("ZY_PHP_KEYWORD", ZyLanguage.INSTANCE);
    public static final IElementType PHP_FUNCTION = new IElementType("ZY_PHP_FUNCTION", ZyLanguage.INSTANCE);
    public static final IElementType PHP_VARIABLE = new IElementType("ZY_PHP_VARIABLE", ZyLanguage.INSTANCE);
    public static final IElementType PHP_CONSTANT = new IElementType("ZY_PHP_CONSTANT", ZyLanguage.INSTANCE);
}
