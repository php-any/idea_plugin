package com.company.plugin.completion

import com.company.plugin.lsp.ZyLspService
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList

/**
 * ZY 代码补全贡献者
 * 基于 LSP 提供代码补全功能
 */
class ZyCompletionContributor : CompletionContributor() {
    
    companion object {
        private val LOG = Logger.getInstance(ZyCompletionContributor::class.java)
    }
    
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            ZyLspCompletionProvider()
        )
    }
    
    private class ZyLspCompletionProvider : CompletionProvider<CompletionParameters>() {
        
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val project = parameters.editor.project ?: return
            val file = parameters.originalFile.virtualFile ?: return
            
            // 只处理 .zy 文件
            if (!file.name.endsWith(".zy")) {
                return
            }
            
            try {
                val lspService = project.getService(ZyLspService::class.java)
                if (lspService == null || !lspService.isStarted()) {
                    LOG.debug("LSP service not available or not started")
                    return
                }
                
                val document = parameters.editor.document
                val offset = parameters.offset
                val lineNumber = document.getLineNumber(offset)
                val lineStartOffset = document.getLineStartOffset(lineNumber)
                val character = offset - lineStartOffset
                
                val uri = file.url
                // 将当前文档内容 didOpen 到 LSP（只执行一次）
                val text = document.text
                lspService.ensureDidOpen(uri, "zy", text)
                
                LOG.debug("Requesting completion at ${file.name}:${lineNumber}:${character}")
                
                val completionFuture = lspService.getCompletion(uri, lineNumber, character)
                
                // 等待 LSP 响应（设置超时）
                try {
                    val completionResult = completionFuture.get(1, java.util.concurrent.TimeUnit.SECONDS)
                    
                    if (completionResult.isLeft) {
                        // List<CompletionItem>
                        val items = completionResult.left
                        for (item in items) {
                            result.addElement(createLookupElement(item))
                        }
                        LOG.debug("Added ${items.size} completion items")
                    } else {
                        // CompletionList
                        val completionList = completionResult.right
                        for (item in completionList.items) {
                            result.addElement(createLookupElement(item))
                        }
                        LOG.debug("Added ${completionList.items.size} completion items from list")
                    }
                } catch (e: Exception) {
                    LOG.debug("LSP completion request failed or timed out", e)
                }
                
            } catch (e: Exception) {
                LOG.error("Error in LSP completion", e)
            }
        }
        
        /**
         * 将 LSP CompletionItem 转换为 IntelliJ LookupElement
         */
        private fun createLookupElement(item: CompletionItem): LookupElementBuilder {
            var builder = LookupElementBuilder.create(item.insertText ?: item.label)
                .withPresentableText(item.label)
            
            // 添加类型信息
            item.detail?.let { detail ->
                builder = builder.withTypeText(detail, true)
            }
            
            // 添加尾部文本
            item.documentation?.let { doc ->
                if (doc.isLeft && doc.left is String) {
                    val docString = doc.left as String
                    if (docString.length <= 50) {
                        builder = builder.withTailText(" - $docString", true)
                    }
                }
            }
            
            // 设置图标（根据 CompletionItemKind）
            item.kind?.let { kind ->
                builder = builder.withIcon(getIconForKind(kind))
            }
            
            return builder
        }
        
        /**
         * 根据 CompletionItemKind 获取对应的图标
         */
        private fun getIconForKind(kind: org.eclipse.lsp4j.CompletionItemKind): javax.swing.Icon? {
            return when (kind) {
                org.eclipse.lsp4j.CompletionItemKind.Function -> com.intellij.icons.AllIcons.Nodes.Function
                org.eclipse.lsp4j.CompletionItemKind.Variable -> com.intellij.icons.AllIcons.Nodes.Variable
                org.eclipse.lsp4j.CompletionItemKind.Class -> com.intellij.icons.AllIcons.Nodes.Class
                org.eclipse.lsp4j.CompletionItemKind.Interface -> com.intellij.icons.AllIcons.Nodes.Interface
                org.eclipse.lsp4j.CompletionItemKind.Module -> com.intellij.icons.AllIcons.Nodes.Module
                org.eclipse.lsp4j.CompletionItemKind.Property -> com.intellij.icons.AllIcons.Nodes.Property
                org.eclipse.lsp4j.CompletionItemKind.Keyword -> com.intellij.icons.AllIcons.Nodes.Static
                else -> null
            }
        }
    }
}