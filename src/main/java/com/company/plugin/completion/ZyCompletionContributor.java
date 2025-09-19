package com.company.plugin.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * ZY 代码补全贡献者
 * 基于本地 Java 实现提供代码补全功能，不依赖 LSP
 */
public class ZyCompletionContributor extends CompletionContributor {
    
    private static final Logger LOG = Logger.getInstance(ZyCompletionContributor.class);
    
    // ZY 语言关键字
    private static final Set<String> ZY_KEYWORDS = new HashSet<String>() {{
        add("function");
        add("if");
        add("else");
        add("for");
        add("while");
        add("return");
        add("var");
        add("let");
        add("const");
        add("true");
        add("false");
        add("null");
        add("undefined");
        add("import");
        add("export");
        add("class");
        add("struct");
        add("interface");
        add("extends");
        add("implements");
        add("public");
        add("private");
        add("protected");
        add("static");
        add("abstract");
        add("final");
    }};
    
    // PHP 关键字
    private static final Set<String> PHP_KEYWORDS = new HashSet<String>() {{
        add("if");
        add("else");
        add("elseif");
        add("endif");
        add("while");
        add("endwhile");
        add("for");
        add("foreach");
        add("endforeach");
        add("do");
        add("switch");
        add("case");
        add("default");
        add("break");
        add("continue");
        add("goto");
        add("function");
        add("class");
        add("struct");
        add("interface");
        add("trait");
        add("namespace");
        add("use");
        add("as");
        add("public");
        add("private");
        add("protected");
        add("static");
        add("abstract");
        add("final");
        add("extends");
        add("implements");
        add("new");
        add("clone");
        add("instanceof");
        add("global");
        add("const");
        add("define");
        add("defined");
        add("try");
        add("catch");
        add("finally");
        add("throw");
        add("include");
        add("include_once");
        add("require");
        add("require_once");
        add("return");
        add("yield");
        add("echo");
        add("print");
        add("print_r");
        add("var_dump");
        add("isset");
        add("empty");
        add("unset");
        add("die");
        add("exit");
        add("array");
        add("string");
        add("int");
        add("float");
        add("bool");
        add("object");
        add("mixed");
        add("void");
        add("callable");
        add("iterable");
        add("resource");
        add("null");
        add("false");
        add("true");
    }};
    
    // PHP 内置函数
    private static final Set<String> PHP_FUNCTIONS = new HashSet<String>() {{
        // 字符串函数
        add("strlen");
        add("strpos");
        add("str_replace");
        add("substr");
        add("trim");
        add("ltrim");
        add("rtrim");
        add("strtolower");
        add("strtoupper");
        add("ucfirst");
        add("ucwords");
        add("str_split");
        add("explode");
        add("implode");
        add("sprintf");
        add("printf");
        add("htmlspecialchars");
        add("htmlentities");
        add("strip_tags");
        
        // 数组函数
        add("array");
        add("count");
        add("sizeof");
        add("array_push");
        add("array_pop");
        add("array_shift");
        add("array_unshift");
        add("array_merge");
        add("array_slice");
        add("array_splice");
        add("array_keys");
        add("array_values");
        add("array_search");
        add("in_array");
        add("array_unique");
        add("array_reverse");
        add("sort");
        add("rsort");
        add("asort");
        add("arsort");
        add("ksort");
        add("krsort");
        add("usort");
        add("uasort");
        add("uksort");
        
        // 文件系统函数
        add("file_exists");
        add("is_file");
        add("is_dir");
        add("file_get_contents");
        add("file_put_contents");
        add("fopen");
        add("fclose");
        add("fread");
        add("fwrite");
        add("fgets");
        add("fgetc");
        add("feof");
        add("fseek");
        add("ftell");
        add("copy");
        add("move_uploaded_file");
        add("unlink");
        add("mkdir");
        add("rmdir");
        add("chmod");
        add("chown");
        
        // 数学函数
        add("abs");
        add("ceil");
        add("floor");
        add("round");
        add("min");
        add("max");
        add("rand");
        add("mt_rand");
        add("pi");
        add("pow");
        add("sqrt");
        add("sin");
        add("cos");
        add("tan");
        add("asin");
        add("acos");
        add("atan");
        add("log");
        add("exp");
        
        // 日期时间函数
        add("date");
        add("time");
        add("strtotime");
        add("mktime");
        add("getdate");
        add("gmdate");
        add("microtime");
        
        // 其他常用函数
        add("json_encode");
        add("json_decode");
        add("serialize");
        add("unserialize");
        add("var_dump");
        add("print_r");
        add("gettype");
        add("is_array");
        add("is_string");
        add("is_numeric");
        add("is_null");
        add("is_bool");
        add("filter_var");
        add("filter_input");
        add("preg_match");
        add("preg_replace");
        add("preg_split");
    }};
    
    // PHP 常量
    private static final Set<String> PHP_CONSTANTS = new HashSet<String>() {{
        add("PHP_VERSION");
        add("PHP_OS");
        add("PHP_EOL");
        add("PHP_INT_MAX");
        add("PHP_INT_MIN");
        add("PHP_FLOAT_MAX");
        add("PHP_FLOAT_MIN");
        add("PHP_SAPI");
        add("PHP_BINARY");
        add("E_ERROR");
        add("E_WARNING");
        add("E_PARSE");
        add("E_NOTICE");
        add("E_CORE_ERROR");
        add("E_CORE_WARNING");
        add("E_COMPILE_ERROR");
        add("E_COMPILE_WARNING");
        add("E_USER_ERROR");
        add("E_USER_WARNING");
        add("E_USER_NOTICE");
        add("E_STRICT");
        add("E_RECOVERABLE_ERROR");
        add("E_DEPRECATED");
        add("E_USER_DEPRECATED");
        add("E_ALL");
        add("DIRECTORY_SEPARATOR");
        add("PATH_SEPARATOR");
        add("FILE_SEPARATOR");
        add("TRUE");
        add("FALSE");
        add("NULL");
        add("M_PI");
        add("M_E");
        add("M_LOG2E");
        add("M_LOG10E");
        add("M_LN2");
        add("M_LN10");
    }};
    
    public ZyCompletionContributor() {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            new ZyCompletionProvider()
        );
    }
    
    private static class ZyCompletionProvider extends CompletionProvider<CompletionParameters> {
        
        @Override
        protected void addCompletions(
                @NotNull CompletionParameters parameters,
                @NotNull ProcessingContext context,
                @NotNull CompletionResultSet result
        ) {
            Project project = parameters.getEditor().getProject();
            if (project == null) return;
            
            VirtualFile file = parameters.getOriginalFile().getVirtualFile();
            if (file == null) return;
            
            // 只处理 .zy 文件
            if (!file.getName().endsWith(".zy")) {
                return;
            }
            
            try {
                LOG.debug("Adding completions for file: " + file.getName());
                
                // 获取当前光标位置的文本上下文
                String prefix = getPrefix(parameters);
                
                // 添加关键字补全
                addKeywordCompletions(result, prefix);
                
                // 添加 PHP 关键字补全
                addPhpKeywordCompletions(result, prefix);
                
                // 添加 PHP 函数补全
                addPhpFunctionCompletions(result, prefix);
                
                // 添加 PHP 常量补全
                addPhpConstantCompletions(result, prefix);
                
                // 添加代码片段补全
                addSnippetCompletions(result, prefix);
                
                LOG.debug("Added completions with prefix: " + prefix);
                
            } catch (Exception e) {
                LOG.error("Error in completion", e);
            }
        }
        
        /**
         * 获取当前光标位置的前缀文本
         */
        private String getPrefix(CompletionParameters parameters) {
            var document = parameters.getEditor().getDocument();
            int offset = parameters.getOffset();
            
            // 向前查找，直到遇到空白字符或特殊字符
            int start = offset;
            while (start > 0) {
                char c = document.getCharsSequence().charAt(start - 1);
                if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c) && c != '_' && c != '$') {
                    break;
                }
                start--;
            }
            
            return document.getCharsSequence().subSequence(start, offset).toString();
        }
        
        /**
         * 添加 ZY 关键字补全
         */
        private void addKeywordCompletions(CompletionResultSet result, String prefix) {
            for (String keyword : ZY_KEYWORDS) {
                if (keyword.toLowerCase().startsWith(prefix.toLowerCase())) {
                    result.addElement(
                        LookupElementBuilder.create(keyword)
                            .withTypeText("ZY Keyword")
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Static)
                    );
                }
            }
        }
        
        /**
         * 添加 PHP 关键字补全
         */
        private void addPhpKeywordCompletions(CompletionResultSet result, String prefix) {
            for (String keyword : PHP_KEYWORDS) {
                if (keyword.toLowerCase().startsWith(prefix.toLowerCase())) {
                    result.addElement(
                        LookupElementBuilder.create(keyword)
                            .withTypeText("PHP Keyword")
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Static)
                    );
                }
            }
        }
        
        /**
         * 添加 PHP 函数补全
         */
        private void addPhpFunctionCompletions(CompletionResultSet result, String prefix) {
            for (String function : PHP_FUNCTIONS) {
                if (function.toLowerCase().startsWith(prefix.toLowerCase())) {
                    result.addElement(
                        LookupElementBuilder.create(function + "()")
                            .withTypeText("PHP Function")
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Function)
                            .withInsertHandler((context, item) -> {
                                // 将光标定位到括号中间
                                int offset = context.getStartOffset() + function.length() + 1;
                                context.getEditor().getCaretModel().moveToOffset(offset);
                            })
                    );
                }
            }
        }
        
        /**
         * 添加 PHP 常量补全
         */
        private void addPhpConstantCompletions(CompletionResultSet result, String prefix) {
            for (String constant : PHP_CONSTANTS) {
                if (constant.toLowerCase().startsWith(prefix.toLowerCase())) {
                    result.addElement(
                        LookupElementBuilder.create(constant)
                            .withTypeText("PHP Constant")
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Constant)
                    );
                }
            }
        }
        
        /**
         * 添加代码片段补全
         */
        private void addSnippetCompletions(CompletionResultSet result, String prefix) {
            // 函数定义片段
            if ("function".startsWith(prefix.toLowerCase())) {
                result.addElement(
                    LookupElementBuilder.create("function")
                        .withTypeText("Function Definition")
                        .withIcon(com.intellij.icons.AllIcons.Nodes.Function)
                        .withInsertHandler((context, item) -> {
                            // 先删除已经插入的 "function" 文本
                            int startOffset = context.getStartOffset();
                            int endOffset = startOffset + "function".length();
                            context.getDocument().deleteString(startOffset, endOffset);
                            
                            // 插入完整的函数定义代码片段
                            String snippet = "function ${1:functionName}(${2:parameters}) {\n    ${3:// function body}\n    return ${4:value};\n}";
                            insertSnippet(context, snippet);
                        })
                );
            }
            
            // if 语句片段
            if ("if".startsWith(prefix.toLowerCase())) {
                result.addElement(
                    LookupElementBuilder.create("if")
                        .withTypeText("If Statement")
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Static)
                        .withInsertHandler((context, item) -> {
                            // 先删除已经插入的 "if" 文本
                            int startOffset = context.getStartOffset();
                            int endOffset = startOffset + "if".length();
                            context.getDocument().deleteString(startOffset, endOffset);
                            
                            // 插入完整的 if 语句代码片段
                            String snippet = "if (${1:condition}) {\n    ${2:// code}\n}";
                            insertSnippet(context, snippet);
                        })
                );
            }
            
            // for 循环片段
            if ("for".startsWith(prefix.toLowerCase())) {
                result.addElement(
                    LookupElementBuilder.create("for")
                        .withTypeText("For Loop")
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Static)
                        .withInsertHandler((context, item) -> {
                            // 先删除已经插入的 "for" 文本
                            int startOffset = context.getStartOffset();
                            int endOffset = startOffset + "for".length();
                            context.getDocument().deleteString(startOffset, endOffset);
                            
                            // 插入完整的 for 循环代码片段
                            String snippet = "for (${1:initialization}; ${2:condition}; ${3:increment}) {\n    ${4:// code}\n}";
                            insertSnippet(context, snippet);
                        })
                );
            }
            
            // while 循环片段
            if ("while".startsWith(prefix.toLowerCase())) {
                result.addElement(
                    LookupElementBuilder.create("while")
                        .withTypeText("While Loop")
                            .withIcon(com.intellij.icons.AllIcons.Nodes.Static)
                        .withInsertHandler((context, item) -> {
                            // 先删除已经插入的 "while" 文本
                            int startOffset = context.getStartOffset();
                            int endOffset = startOffset + "while".length();
                            context.getDocument().deleteString(startOffset, endOffset);
                            
                            // 插入完整的 while 循环代码片段
                            String snippet = "while (${1:condition}) {\n    ${2:// code}\n}";
                            insertSnippet(context, snippet);
                        })
                );
            }
            
            // class 定义片段
            if ("class".startsWith(prefix.toLowerCase())) {
                result.addElement(
                    LookupElementBuilder.create("class")
                        .withTypeText("Class Definition")
                        .withIcon(com.intellij.icons.AllIcons.Nodes.Class)
                        .withInsertHandler((context, item) -> {
                            // 先删除已经插入的 "class" 文本
                            int startOffset = context.getStartOffset();
                            int endOffset = startOffset + "class".length();
                            context.getDocument().deleteString(startOffset, endOffset);
                            
                            // 插入完整的类定义代码片段
                            String snippet = "class ${1:ClassName} {\n    ${2:// class body}\n}";
                            insertSnippet(context, snippet);
                        })
                );
            }
        }
        
        /**
         * 插入代码片段
         */
        private void insertSnippet(InsertionContext context, String snippet) {
            // 处理代码片段，将 ${n:placeholder} 替换为占位符文本
            String processedSnippet = snippet.replaceAll("\\$\\{([0-9]+):([^}]*)\\}", "$2");
            // 移除剩余的 ${n} 占位符（没有默认值的）
            processedSnippet = processedSnippet.replaceAll("\\$\\{([0-9]+)\\}", "");
            
            // 插入文本
            context.getDocument().insertString(context.getStartOffset(), processedSnippet);
            
            // 查找第一个占位符并定位光标
            String[] placeholders = {"functionName", "ClassName", "condition", "initialization", "parameters"};
            for (String placeholder : placeholders) {
                int placeholderIndex = processedSnippet.indexOf(placeholder);
                if (placeholderIndex >= 0) {
                    int offset = context.getStartOffset() + placeholderIndex;
                    context.getEditor().getCaretModel().moveToOffset(offset);
                    // 选中占位符文本，方便用户直接输入
                    context.getEditor().getSelectionModel().setSelection(offset, offset + placeholder.length());
                    break;
                }
            }
        }
    }
}
