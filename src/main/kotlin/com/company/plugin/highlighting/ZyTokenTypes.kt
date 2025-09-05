package com.company.plugin.highlighting

import com.company.plugin.language.ZyLanguage
import com.intellij.psi.tree.IElementType

/**
 * ZY 语言标记类型
 * 基于 TextMate 语法文件 origami.tmLanguage.json
 * 支持 ZY 语言和 PHP 关键字
 */
class ZyTokenTypes {
    
    companion object {
        // 通用标记类型
        val KEYWORD = IElementType("ZY_KEYWORD", ZyLanguage)
        val STRING = IElementType("ZY_STRING", ZyLanguage)
        val NUMBER = IElementType("ZY_NUMBER", ZyLanguage)
        val COMMENT = IElementType("ZY_COMMENT", ZyLanguage)
        val IDENTIFIER = IElementType("ZY_IDENTIFIER", ZyLanguage)
        val OPERATOR = IElementType("ZY_OPERATOR", ZyLanguage)
        
        // PHP 特定标记类型
        val PHP_KEYWORD = IElementType("ZY_PHP_KEYWORD", ZyLanguage)
        val PHP_FUNCTION = IElementType("ZY_PHP_FUNCTION", ZyLanguage)
        val PHP_VARIABLE = IElementType("ZY_PHP_VARIABLE", ZyLanguage)
        val PHP_CONSTANT = IElementType("ZY_PHP_CONSTANT", ZyLanguage)
    }
}
