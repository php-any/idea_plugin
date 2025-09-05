package com.company.plugin.highlighting

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * ZY 词法分析器
 * 基于 TextMate 语法文件 origami.tmLanguage.json 实现
 */
class ZyLexer : LexerBase() {
    
    private var buffer: CharSequence? = null
    private var bufferEnd: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null
    
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        this.tokenType = null
        advance()
    }
    
    override fun getState(): Int = 0
    
    override fun getTokenType(): IElementType? = tokenType
    
    override fun getTokenStart(): Int = tokenStart
    
    override fun getTokenEnd(): Int = tokenEnd
    
    /**
     * 前进到下一个标记
     * 根据当前字符类型识别并创建相应的标记
     */
    override fun advance() {
        tokenStart = tokenEnd
        if (tokenEnd >= bufferEnd) {
            tokenType = null
            return
        }
        
        val currentChar = buffer!![tokenEnd]
        tokenEnd++
        
        tokenType = when {
            currentChar.isWhitespace() -> {
                // 跳过空白字符（空格、制表符、换行符等）
                while (tokenEnd < bufferEnd && buffer!![tokenEnd].isWhitespace()) {
                    tokenEnd++
                }
                advance() // 递归调用跳过空白，继续处理下一个非空白字符
                return
            }
            currentChar == '/' && tokenEnd < bufferEnd && buffer!![tokenEnd] == '/' -> {
                // 单行注释 // 的处理
                while (tokenEnd < bufferEnd && buffer!![tokenEnd] != '\n') {
                    tokenEnd++
                }
                ZyTokenTypes.COMMENT
            }
            currentChar == '/' && tokenEnd < bufferEnd && buffer!![tokenEnd] == '*' -> {
                // 多行注释 /* */ 的处理
                tokenEnd++
                while (tokenEnd < bufferEnd - 1) {
                    if (buffer!![tokenEnd] == '*' && buffer!![tokenEnd + 1] == '/') {
                        tokenEnd += 2
                        break
                    }
                    tokenEnd++
                }
                ZyTokenTypes.COMMENT
            }
            currentChar == '"' || currentChar == '\'' -> {
                // 字符串字面量的处理（支持单引号和双引号）
                val quote = currentChar
                while (tokenEnd < bufferEnd && buffer!![tokenEnd] != quote) {
                    if (buffer!![tokenEnd] == '\\' && tokenEnd + 1 < bufferEnd) {
                        tokenEnd += 2 // 跳过转义字符（如 \n, \t, \" 等）
                    } else {
                        tokenEnd++
                    }
                }
                if (tokenEnd < bufferEnd) tokenEnd++
                ZyTokenTypes.STRING
            }
            currentChar.isDigit() -> {
                // 数字字面量的处理（整数和浮点数）
                while (tokenEnd < bufferEnd && (buffer!![tokenEnd].isDigit() || buffer!![tokenEnd] == '.')) {
                    tokenEnd++
                }
                ZyTokenTypes.NUMBER
            }
            currentChar.isLetter() || currentChar == '_' -> {
                // 标识符或关键字的处理
                while (tokenEnd < bufferEnd && (buffer!![tokenEnd].isLetterOrDigit() || buffer!![tokenEnd] == '_')) {
                    tokenEnd++
                }
                val text = buffer!!.subSequence(tokenStart, tokenEnd).toString()
                if (isKeyword(text)) ZyTokenTypes.KEYWORD else ZyTokenTypes.IDENTIFIER
            }
            else -> {
                // 操作符和其他符号的处理
                ZyTokenTypes.OPERATOR
            }
        }
    }
    
    /**
     * 检查文本是否为关键字
     * @param text 要检查的文本
     * @return 如果是关键字返回 true，否则返回 false
     */
    private fun isKeyword(text: String): Boolean {
        // ZY 语言支持的关键字列表
        val keywords = setOf(
            "function", "if", "else", "for", "while", "return", "var", "let", "const",
            "true", "false", "null", "undefined", "import", "export", "class", "interface"
        )
        return keywords.contains(text)
    }
    
    override fun getBufferSequence(): CharSequence = buffer!!
    
    override fun getBufferEnd(): Int = bufferEnd
}
