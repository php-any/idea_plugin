package com.company.plugin.highlighting

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import com.intellij.psi.TokenType

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
        if (startOffset < endOffset) {
            advance()
        }
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

        // 处理空白字符为独立 token，确保 token 序列连续且不出现空洞
        if (currentChar.isWhitespace()) {
            while (tokenEnd < bufferEnd && buffer!![tokenEnd].isWhitespace()) {
                tokenEnd++
            }
            tokenType = TokenType.WHITE_SPACE
            return
        }

        // 简化的token识别逻辑，减少复杂性
        when {
            currentChar == '/' && tokenEnd + 1 < bufferEnd -> {
                if (buffer!![tokenEnd + 1] == '/') {
                    // 单行注释
                    tokenEnd += 2
                    while (tokenEnd < bufferEnd && buffer!![tokenEnd] != '\n') {
                        tokenEnd++
                    }
                    tokenType = ZyTokenTypes.COMMENT
                } else if (buffer!![tokenEnd + 1] == '*') {
                    // 多行注释
                    tokenEnd += 2
                    while (tokenEnd < bufferEnd - 1) {
                        if (buffer!![tokenEnd] == '*' && buffer!![tokenEnd + 1] == '/') {
                            tokenEnd += 2
                            break
                        }
                        tokenEnd++
                    }
                    tokenType = ZyTokenTypes.COMMENT
                } else {
                    tokenEnd++
                    tokenType = ZyTokenTypes.OPERATOR
                }
            }
            currentChar == '"' || currentChar == '\'' -> {
                // 字符串字面量
                val quote = currentChar
                tokenEnd++
                while (tokenEnd < bufferEnd && buffer!![tokenEnd] != quote) {
                    if (buffer!![tokenEnd] == '\\' && tokenEnd + 1 < bufferEnd) {
                        tokenEnd += 2
                    } else {
                        tokenEnd++
                    }
                }
                if (tokenEnd < bufferEnd) tokenEnd++
                tokenType = ZyTokenTypes.STRING
            }
            currentChar.isDigit() -> {
                // 数字字面量
                tokenEnd++
                while (tokenEnd < bufferEnd && (buffer!![tokenEnd].isDigit() || buffer!![tokenEnd] == '.')) {
                    tokenEnd++
                }
                tokenType = ZyTokenTypes.NUMBER
            }
            currentChar == '$' -> {
                // PHP 变量 - 包含 $ 符号本身
                tokenEnd++
                while (tokenEnd < bufferEnd && (buffer!![tokenEnd].isLetterOrDigit() || buffer!![tokenEnd] == '_')) {
                    tokenEnd++
                }
                tokenType = ZyTokenTypes.PHP_VARIABLE
            }
            currentChar.isLetter() || currentChar == '_' -> {
                // 标识符或关键字
                tokenEnd++
                while (tokenEnd < bufferEnd && (buffer!![tokenEnd].isLetterOrDigit() || buffer!![tokenEnd] == '_')) {
                    tokenEnd++
                }
                val text = buffer!!.subSequence(tokenStart, tokenEnd).toString()
                tokenType = getTokenTypeForText(text)
            }
            else -> {
                // 其他字符
                tokenEnd++
                tokenType = ZyTokenTypes.OPERATOR
            }
        }
    }
    
    /**
     * 根据文本内容确定标记类型
     * @param text 要检查的文本
     * @return 对应的标记类型
     */
    private fun getTokenTypeForText(text: String): IElementType {
        return when {
            isZyKeyword(text) -> ZyTokenTypes.KEYWORD
            isPhpKeyword(text) -> ZyTokenTypes.PHP_KEYWORD
            isPhpFunction(text) -> ZyTokenTypes.PHP_FUNCTION
            isPhpConstant(text) -> ZyTokenTypes.PHP_CONSTANT
            else -> ZyTokenTypes.IDENTIFIER
        }
    }
    
    /**
     * 检查文本是否为 ZY 语言关键字
     * @param text 要检查的文本
     * @return 如果是 ZY 关键字返回 true，否则返回 false
     */
    private fun isZyKeyword(text: String): Boolean {
        val zyKeywords = setOf(
            "function", "if", "else", "for", "while", "return", "var", "let", "const",
            "true", "false", "null", "undefined", "import", "export", "class", "interface"
        )
        return zyKeywords.contains(text)
    }
    
    /**
     * 检查文本是否为 PHP 关键字
     * @param text 要检查的文本
     * @return 如果是 PHP 关键字返回 true，否则返回 false
     */
    private fun isPhpKeyword(text: String): Boolean {
        val phpKeywords = setOf(
            // PHP 控制结构
            "if", "else", "elseif", "endif", "while", "endwhile", "for", "foreach", "endforeach",
            "do", "switch", "case", "default", "break", "continue", "goto",
            
            // PHP 函数和类
            "function", "class", "interface", "trait", "namespace", "use", "as",
            "public", "private", "protected", "static", "abstract", "final",
            "extends", "implements", "new", "clone", "instanceof",
            
            // PHP 变量和常量
            "global", "static", "const", "define", "defined",
            
            // PHP 异常处理
            "try", "catch", "finally", "throw",
            
            // PHP 包含和引用
            "include", "include_once", "require", "require_once",
            
            // PHP 其他关键字
            "return", "yield", "echo", "print", "print_r", "var_dump",
            "isset", "empty", "unset", "die", "exit",
            
            // PHP 类型声明
            "array", "string", "int", "float", "bool", "object", "mixed", "void",
            "callable", "iterable", "resource", "null", "false", "true",
            
            // PHP 魔术方法
            "__construct", "__destruct", "__call", "__callStatic", "__get", "__set",
            "__isset", "__unset", "__sleep", "__wakeup", "__toString", "__invoke",
            "__set_state", "__clone", "__debugInfo"
        )
        return phpKeywords.contains(text)
    }
    
    /**
     * 检查文本是否为 PHP 内置函数
     * @param text 要检查的文本
     * @return 如果是 PHP 函数返回 true，否则返回 false
     */
    private fun isPhpFunction(text: String): Boolean {
        val phpFunctions = setOf(
            // 字符串函数
            "strlen", "strpos", "str_replace", "substr", "trim", "ltrim", "rtrim",
            "strtolower", "strtoupper", "ucfirst", "ucwords", "str_split", "explode", "implode",
            "sprintf", "printf", "htmlspecialchars", "htmlentities", "strip_tags",
            
            // 数组函数
            "array", "count", "sizeof", "array_push", "array_pop", "array_shift", "array_unshift",
            "array_merge", "array_slice", "array_splice", "array_keys", "array_values",
            "array_search", "in_array", "array_unique", "array_reverse", "sort", "rsort",
            "asort", "arsort", "ksort", "krsort", "usort", "uasort", "uksort",
            
            // 文件系统函数
            "file_exists", "is_file", "is_dir", "file_get_contents", "file_put_contents",
            "fopen", "fclose", "fread", "fwrite", "fgets", "fgetc", "feof", "fseek", "ftell",
            "copy", "move_uploaded_file", "unlink", "mkdir", "rmdir", "chmod", "chown",
            
            // 数学函数
            "abs", "ceil", "floor", "round", "min", "max", "rand", "mt_rand", "pi",
            "pow", "sqrt", "sin", "cos", "tan", "asin", "acos", "atan", "log", "exp",
            
            // 日期时间函数
            "date", "time", "strtotime", "mktime", "getdate", "gmdate", "microtime",
            
            // 数据库函数
            "mysql_connect", "mysql_query", "mysql_fetch_array", "mysql_fetch_assoc",
            "mysqli_connect", "mysqli_query", "mysqli_fetch_array", "mysqli_fetch_assoc",
            "pdo", "PDO",
            
            // 其他常用函数
            "json_encode", "json_decode", "serialize", "unserialize", "var_dump", "print_r",
            "gettype", "is_array", "is_string", "is_numeric", "is_null", "is_bool",
            "filter_var", "filter_input", "preg_match", "preg_replace", "preg_split"
        )
        return phpFunctions.contains(text)
    }
    
    /**
     * 检查文本是否为 PHP 常量
     * @param text 要检查的文本
     * @return 如果是 PHP 常量返回 true，否则返回 false
     */
    private fun isPhpConstant(text: String): Boolean {
        val phpConstants = setOf(
            // PHP 预定义常量
            "PHP_VERSION", "PHP_OS", "PHP_EOL", "PHP_INT_MAX", "PHP_INT_MIN",
            "PHP_FLOAT_MAX", "PHP_FLOAT_MIN", "PHP_SAPI", "PHP_BINARY",
            
            // 错误级别常量
            "E_ERROR", "E_WARNING", "E_PARSE", "E_NOTICE", "E_CORE_ERROR", "E_CORE_WARNING",
            "E_COMPILE_ERROR", "E_COMPILE_WARNING", "E_USER_ERROR", "E_USER_WARNING",
            "E_USER_NOTICE", "E_STRICT", "E_RECOVERABLE_ERROR", "E_DEPRECATED", "E_USER_DEPRECATED",
            "E_ALL",
            
            // 文件系统常量
            "DIRECTORY_SEPARATOR", "PATH_SEPARATOR", "FILE_SEPARATOR",
            
            // 其他常量
            "TRUE", "FALSE", "NULL", "M_PI", "M_E", "M_LOG2E", "M_LOG10E", "M_LN2", "M_LN10"
        )
        return phpConstants.contains(text)
    }
    
    override fun getBufferSequence(): CharSequence = buffer!!
    
    override fun getBufferEnd(): Int = bufferEnd
}
