package com.company.plugin.completion

import com.company.plugin.lsp.ZyLspService
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Position

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
                
                // 通知 LSP 文档变更（确保同步最新内容）
                lspService.notifyDocumentChange(uri, text)
                
                LOG.debug("Requesting completion at ${file.name}:${lineNumber}:${character}")
                
                val completionFuture = lspService.getCompletion(uri, lineNumber, character)
                
                // 等待 LSP 响应（设置超时）
                try {
                    val completionResult = completionFuture.get(3, java.util.concurrent.TimeUnit.SECONDS)
                    
                    if (completionResult.isLeft) {
                        // List<CompletionItem>
                        val items = completionResult.left
                        for (item in items) {
                            result.addElement(createLookupElement(item, document, offset))
                        }
                        LOG.debug("Added ${items.size} completion items")
                    } else {
                        // CompletionList
                        val completionList = completionResult.right
                        for (item in completionList.items) {
                            result.addElement(createLookupElement(item, document, offset))
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
        private fun createLookupElement(item: CompletionItem, document: com.intellij.openapi.editor.Document, offset: Int): LookupElement {
            // 使用 insertText 或 label 作为补全文本
            val lookupString = item.insertText ?: item.label
            
            var builder = LookupElementBuilder.create(lookupString)
                .withPresentableText(item.label)
            
            // 添加类型信息
            item.detail?.let { detail ->
                builder = builder.withTypeText(detail, true)
            }
            
            // 添加文档信息作为尾部文本
            item.documentation?.let { doc ->
                when {
                    doc.isLeft && doc.left is String -> {
                        val docString = doc.left as String
                        if (docString.isNotEmpty()) {
                            builder = builder.withTailText(" $docString", true)
                        }
                    }
                    doc.isRight -> {
                        // 处理 MarkupContent 类型的文档
                        val markupContent = doc.right
                        if (markupContent != null && markupContent.value.isNotEmpty()) {
                            val preview = if (markupContent.value.length > 50) {
                                markupContent.value.substring(0, 47) + "..."
                            } else {
                                markupContent.value
                            }
                            builder = builder.withTailText(" $preview", true)
                        }
                    }
                }
            }
            
            // 设置图标（根据 CompletionItemKind）
            val icon = getIconForKind(item.kind)
            if (icon != null) {
                builder = builder.withIcon(icon)
            }
            
            // 处理文本替换范围与 Snippet 扩展
            val textEdit = item.textEdit
            val isSnippet = item.insertTextFormat == InsertTextFormat.Snippet

            if (isSnippet) {
                // 代码片段：将 LSP Snippet 转换为可插入文本，并尽量定位到首个光标位
                val snippetText = item.insertText ?: when {
                    textEdit != null && textEdit.isLeft -> textEdit.left.newText
                    else -> item.label
                }
                val parsed = parseLspSnippet(snippetText)
                builder = builder.withInsertHandler { context, _ ->
                    val doc = context.document
                    val caretModel = context.editor.caretModel

                    // 先移除默认插入的 lookupString，避免重复文本
                    if (context.startOffset <= context.tailOffset) {
                        doc.replaceString(context.startOffset, context.tailOffset, "")
                    }

                    if (textEdit != null && textEdit.isLeft) {
                        val edit = textEdit.left
                        val startOffset = toOffset(doc, edit.range.start)
                        val endOffset = toOffset(doc, edit.range.end)
                        doc.replaceString(startOffset, endOffset, parsed.text)
                        val caretOffset = startOffset + parsed.caretOffset.coerceAtLeast(0).coerceAtMost(parsed.text.length)
                        caretModel.moveToOffset(caretOffset)
                    } else {
                        val insertionOffset = context.startOffset
                        doc.insertString(insertionOffset, parsed.text)
                        val caretOffset = insertionOffset + parsed.caretOffset.coerceAtLeast(0).coerceAtMost(parsed.text.length)
                        caretModel.moveToOffset(caretOffset)
                    }
                }
            } else if (textEdit != null && textEdit.isLeft) {
                val edit = textEdit.left
                builder = builder.withInsertHandler { context, _ ->
                    val doc = context.document
                    // 先移除默认插入的 lookupString，避免重复文本
                    if (context.startOffset <= context.tailOffset) {
                        doc.replaceString(context.startOffset, context.tailOffset, "")
                    }
                    // 使用 TextEdit 中定义的范围进行替换
                    val startOffset = toOffset(doc, edit.range.start)
                    val endOffset = toOffset(doc, edit.range.end)
                    doc.replaceString(startOffset, endOffset, edit.newText)
                    context.editor.caretModel.moveToOffset(startOffset + edit.newText.length)
                }
            }
            
            return builder
        }
        
        /**
         * 将 LSP Position 转换为文档偏移量
         */
        private fun toOffset(document: com.intellij.openapi.editor.Document, position: Position): Int {
            val line = position.line.coerceIn(0, document.lineCount - 1)
            val lineStartOffset = document.getLineStartOffset(line)
            val lineEndOffset = document.getLineEndOffset(line)
            val character = position.character
            
            return (lineStartOffset + character).coerceIn(lineStartOffset, lineEndOffset)
        }
        
        /**
         * 根据 CompletionItemKind 获取对应的图标
         */
        private fun getIconForKind(kind: CompletionItemKind?): javax.swing.Icon? {
            if (kind == null) return null
            
            return when (kind) {
                CompletionItemKind.Function -> com.intellij.icons.AllIcons.Nodes.Function
                CompletionItemKind.Variable -> com.intellij.icons.AllIcons.Nodes.Variable
                CompletionItemKind.Class -> com.intellij.icons.AllIcons.Nodes.Class
                CompletionItemKind.Interface -> com.intellij.icons.AllIcons.Nodes.Interface
                CompletionItemKind.Module -> com.intellij.icons.AllIcons.Nodes.Module
                CompletionItemKind.Property -> com.intellij.icons.AllIcons.Nodes.Property
                CompletionItemKind.Keyword -> com.intellij.icons.AllIcons.Nodes.Static
                CompletionItemKind.Method -> com.intellij.icons.AllIcons.Nodes.Method
                CompletionItemKind.Constructor -> com.intellij.icons.AllIcons.Nodes.Class
                CompletionItemKind.Field -> com.intellij.icons.AllIcons.Nodes.Field
                CompletionItemKind.Enum -> com.intellij.icons.AllIcons.Nodes.Enum
                CompletionItemKind.EnumMember -> com.intellij.icons.AllIcons.Nodes.Enum
                CompletionItemKind.Constant -> com.intellij.icons.AllIcons.Nodes.Constant
                CompletionItemKind.Struct -> com.intellij.icons.AllIcons.Nodes.Class
                CompletionItemKind.Event -> com.intellij.icons.AllIcons.Nodes.ErrorIntroduction
                CompletionItemKind.Operator -> com.intellij.icons.AllIcons.Nodes.Function
                CompletionItemKind.Unit -> com.intellij.icons.AllIcons.Nodes.Static
                CompletionItemKind.Value -> com.intellij.icons.AllIcons.Nodes.Variable
                CompletionItemKind.File -> com.intellij.icons.AllIcons.FileTypes.Text
                CompletionItemKind.Snippet -> com.intellij.icons.AllIcons.Actions.Copy
                CompletionItemKind.Color -> com.intellij.icons.AllIcons.Actions.Colors
                CompletionItemKind.Reference -> com.intellij.icons.AllIcons.Nodes.UpLevel
                else -> null
            }
        }

        /**
         * 解析 LSP Snippet 文本，返回可插入的纯文本与光标相对位置
         * 只实现常见占位符：$1、${1}、${1:default}、${1|a,b|}、$0
         * 复杂表达式会被降级为其文字值
         */
        private fun parseLspSnippet(snippet: String): ParsedSnippetResult {
            var textBuilder = StringBuilder()
            var index = 0
            var firstTabstopOffset: Int? = null
            var caretAbsoluteIndexForFirst: Int? = null

            fun recordFirstCaretIfNeeded(currentOutputLen: Int) {
                if (firstTabstopOffset == null) {
                    firstTabstopOffset = currentOutputLen
                }
            }

            while (index < snippet.length) {
                val ch = snippet[index]
                if (ch == '$') {
                    // 尝试匹配 $0 / $1..$9 / ${...}
                    if (index + 1 < snippet.length && snippet[index + 1].isDigit()) {
                        val num = snippet[index + 1]
                        if (num == '0') {
                            // $0 作为最终光标位置，若未设置首个 tabstop，则使用此处
                            recordFirstCaretIfNeeded(textBuilder.length)
                        } else {
                            // $1..$9 将光标定位到第一个 tabstop 出现位置
                            recordFirstCaretIfNeeded(textBuilder.length)
                        }
                        index += 2
                        continue
                    } else if (index + 1 < snippet.length && snippet[index + 1] == '{') {
                        // 处理 ${...}
                        val start = index + 2
                        var i = start
                        var braceDepth = 1
                        while (i < snippet.length && braceDepth > 0) {
                            val c = snippet[i]
                            if (c == '{') braceDepth++
                            if (c == '}') braceDepth--
                            i++
                        }
                        val content = snippet.substring(start, i - 1)
                        // 解析形如 1:default 或 1|a,b|
                        val colonIdx = content.indexOf(':')
                        val pipeStart = content.indexOf('|')
                        val pipeEnd = if (pipeStart >= 0) content.indexOf('|', pipeStart + 1) else -1
                        val defaultText = when {
                            colonIdx >= 0 -> content.substring(colonIdx + 1)
                            pipeStart >= 0 && pipeEnd > pipeStart -> content.substring(pipeStart + 1, pipeEnd).split(',').firstOrNull() ?: ""
                            else -> ""
                        }
                        // 变量编号，用于判断是否首个 tabstop
                        val varNum = content.takeWhile { it.isDigit() }
                        if (varNum.isNotEmpty() && varNum != "0") {
                            recordFirstCaretIfNeeded(textBuilder.length)
                        } else if (varNum == "0") {
                            recordFirstCaretIfNeeded(textBuilder.length)
                        }
                        textBuilder.append(defaultText)
                        index = i
                        continue
                    }
                } else if (ch == '\\' && index + 1 < snippet.length) {
                    // 处理简单转义 \$、\{、\}
                    val next = snippet[index + 1]
                    if (next == '$' || next == '{' || next == '}') {
                        textBuilder.append(next)
                        index += 2
                        continue
                    }
                }
                textBuilder.append(ch)
                index++
            }

            val finalText = textBuilder.toString()
            val caretOffset = (firstTabstopOffset ?: finalText.length)
            return ParsedSnippetResult(finalText, caretOffset)
        }

        /**
         * 解析后的 Snippet 结果
         */
        private data class ParsedSnippetResult(
            val text: String,
            val caretOffset: Int
        )
    }
}