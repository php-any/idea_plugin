package com.company.plugin.highlighting

import com.company.plugin.language.ZyLanguage
import com.intellij.psi.tree.IElementType

/**
 * ZY 语言标记类型
 * 基于 TextMate 语法文件 origami.tmLanguage.json
 */
class ZyTokenTypes {
    
    companion object {
        val KEYWORD = IElementType("ZY_KEYWORD", ZyLanguage)
        val STRING = IElementType("ZY_STRING", ZyLanguage)
        val NUMBER = IElementType("ZY_NUMBER", ZyLanguage)
        val COMMENT = IElementType("ZY_COMMENT", ZyLanguage)
        val IDENTIFIER = IElementType("ZY_IDENTIFIER", ZyLanguage)
        val OPERATOR = IElementType("ZY_OPERATOR", ZyLanguage)
    }
}
