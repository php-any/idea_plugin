package com.company.plugin.highlighting

import com.company.plugin.language.ZyLanguage
import com.intellij.psi.tree.IElementType

/**
 * ZY 语言标记类型
 * 基于 TextMate 语法文件 origami.tmLanguage.json
 */
class ZyTokenTypes {
    
    companion object {
        val KEYWORD = IElementType("ZY_KEYWORD", ZyLanguage.INSTANCE)
        val STRING = IElementType("ZY_STRING", ZyLanguage.INSTANCE)
        val NUMBER = IElementType("ZY_NUMBER", ZyLanguage.INSTANCE)
        val COMMENT = IElementType("ZY_COMMENT", ZyLanguage.INSTANCE)
        val IDENTIFIER = IElementType("ZY_IDENTIFIER", ZyLanguage.INSTANCE)
        val OPERATOR = IElementType("ZY_OPERATOR", ZyLanguage.INSTANCE)
    }
}
