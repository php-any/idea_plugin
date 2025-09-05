package com.company.plugin.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * ZY 代码补全提供者
 * 基于 LSP 的代码补全功能
 */
class ZyCompletionContributor : CompletionContributor() {
    
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            ZyCompletionProvider()
        )
    }
    
    private class ZyCompletionProvider : CompletionProvider<CompletionParameters>() {
        
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            // 添加关键字补全
            addKeywordCompletions(result)
            
            // 添加 LSP 补全 (将在后续实现)
            addLspCompletions(parameters, result)
        }
        
        /**
         * 添加关键字补全建议
         * @param result 补全结果集合
         */
        private fun addKeywordCompletions(result: CompletionResultSet) {
            // ZY 语言支持的关键字列表
            val keywords = listOf(
                "function", "if", "else", "for", "while", "return", "var", "let", "const",
                "true", "false", "null", "undefined", "import", "export", "class", "interface"
            )
            
            // 为每个关键字创建补全项
            keywords.forEach { keyword ->
                result.addElement(
                    LookupElementBuilder.create(keyword)
                        .withTypeText("keyword")
                        .withTailText(" ZY keyword")
                )
            }
        }
        
        /**
         * 添加 LSP 补全建议
         * @param parameters 补全参数
         * @param result 补全结果集合
         */
        private fun addLspCompletions(
            parameters: CompletionParameters,
            result: CompletionResultSet
        ) {
            // 从 LSP 服务器获取补全建议
            val lspCompletions = ZyLspCompletionService.getCompletions(parameters)
            
            // 为每个 LSP 补全项创建补全元素
            lspCompletions.forEach { completion ->
                result.addElement(
                    LookupElementBuilder.create(completion.text)
                        .withTypeText(completion.kind)
                        .withTailText(" LSP completion")
                        .withInsertHandler(completion.insertHandler)
                )
            }
        }
    }
}
