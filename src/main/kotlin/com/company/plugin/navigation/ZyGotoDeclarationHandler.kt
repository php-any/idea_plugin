package com.company.plugin.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceService
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.company.plugin.lsp.ZyLspService
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import java.util.concurrent.TimeUnit

/**
 * ZY 跳转到定义处理器
 * 使用 LSP definition 能力实现从符号到定义位置的跳转
 */
class ZyGotoDeclarationHandler : GotoDeclarationHandler {

    companion object {
        private val LOG = Logger.getInstance(ZyGotoDeclarationHandler::class.java)
    }

    /**
     * 获取下划线显示的目标元素
     * 这个方法控制 Alt + 光标悬停时显示下划线的范围
     */
    override fun getGotoDeclarationTargets(element: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val project: Project = editor.project ?: return null
        // 当传入的是文件级元素（整文件）时，直接返回 null，避免整文件下划线
        if (element == null || element is com.intellij.psi.PsiFile) {
            return null
        }
        val file: VirtualFile = element.containingFile?.virtualFile ?: return null
        if (!file.name.endsWith(".zy")) return null

        // 先进行词法级“单词”范围判定（字母、数字、下划线）
        // 为兼容点击括号/点号等非单词字符，若当前无单词，尝试左右一位；仍无则使用最小范围以便后续 LSP 多偏移探测
        var wordRange = computeWordRange(editor, offset)
        if (wordRange == null) {
            val doc = editor.document
            val tryOffsets = listOf((offset - 1).coerceAtLeast(0), (offset + 1).coerceAtMost(doc.textLength))
            for (off in tryOffsets) {
                wordRange = computeWordRange(editor, off)
                if (wordRange != null) break
            }
            if (wordRange == null) {
                wordRange = TextRange(offset.coerceAtLeast(0), (offset + 1).coerceAtMost(editor.document.textLength))
                LOG.info("GTD: no word found at caret; use minimal range=$wordRange")
            }
        }
        // 获取光标位置处的精确叶子元素，确保仅在标识符等可导航单元上生效
        val leafAtCaret: PsiElement = element.containingFile.findElementAt(offset) ?: return null
        if (leafAtCaret.textLength == 0 || leafAtCaret.text.isBlank()) return null
        LOG.info("GTD: leaf=${leafAtCaret.javaClass.simpleName} text='${leafAtCaret.text}' range=${leafAtCaret.textRange} wordRange=$wordRange")

        // 引用命中策略：
        // 1) 优先检查叶子元素上的引用
        // 2) 若未命中，向上检查父级元素（最多 3 层），很多语言的调用/成员访问引用挂在父节点
        // 3) 若仍未命中，不再中断，允许回退到基于单词范围的 LSP 请求，提升兼容性（修复类方法跳转）
        fun checkReferenceHit(elem: PsiElement, caretAbsOffset: Int): Boolean {
            val elemRange = elem.textRange ?: return false
            val rel = caretAbsOffset - elemRange.startOffset
            val refs = PsiReferenceService.getService().getReferences(elem, PsiReferenceService.Hints.NO_HINTS)
            if (refs.isNotEmpty()) {
                val hit = refs.any { r -> rel >= r.rangeInElement.startOffset && rel <= r.rangeInElement.endOffset }
                if (hit) return true
            }
            return false
        }
        var refHit = checkReferenceHit(leafAtCaret, offset)
        if (!refHit) {
            var p = leafAtCaret.parent
            var depth = 0
            while (!refHit && p != null && depth < 3) {
                if (checkReferenceHit(p, offset)) refHit = true else { p = p.parent; depth++ }
            }
        }
        if (refHit) {
            LOG.info("GTD: reference range hit; continue to LSP lookup")
        } else {
            LOG.info("GTD: no reference hit on leaf/parents; fallback to word-based LSP lookup")
        }

        return try {
            
            val lspService = project.getService(ZyLspService::class.java) ?: return null
            if (!lspService.isStarted()) {
                LOG.info("LSP service not started")
                return null
            }

            val document = editor.document
            val uri = file.url
            
            // 直接同步文档，不做任何优化判断
            val text = document.text
            lspService.ensureDidOpen(uri, "zy", text)
            lspService.notifyDocumentChange(uri, text)

            
            // 为提升兼容性，尝试多种位置：caret、wordStart、wordEnd-1
            val caretLine = document.getLineNumber(offset)
            val caretLineStart = document.getLineStartOffset(caretLine)
            val caretChar = offset - caretLineStart

            val startPos = wordRange.startOffset
            val startLine = document.getLineNumber(startPos)
            val startLineStart = document.getLineStartOffset(startLine)
            val startChar = startPos - startLineStart

            val endPos = (wordRange.endOffset - 1).coerceAtLeast(wordRange.startOffset)
            val endLine = document.getLineNumber(endPos)
            val endLineStart = document.getLineStartOffset(endLine)
            val endChar = endPos - endLineStart

            // 扩展多种探测偏移，兼容成员访问/方法调用等场景
            fun safeChar(line: Int, ch: Int): Pair<Int, Int> {
                val ln = line.coerceAtLeast(0)
                val lineStart = document.getLineStartOffset(ln)
                val lineEnd = document.getLineEndOffset(ln)
                val maxCh = (lineEnd - lineStart).coerceAtLeast(0)
                return ln to ch.coerceIn(0, maxCh)
            }

            val extraOffsets = buildList {
                // 原始与附近偏移
                add(offset)
                add((offset - 1).coerceAtLeast(0))
                add((offset + 1).coerceAtMost(document.textLength))

                // 单词范围内关键位置：起始、结束-1、以及中点
                add(startPos)
                add((startPos + 1).coerceAtMost(document.textLength))
                add(endPos)
                add((endPos - 1).coerceAtLeast(0))
                val mid = startPos + (endPos - startPos) / 2
                add(mid.coerceIn(startPos, endPos))

                // 处理成员访问符 '.' 或 '->'：若光标在其上，尝试向右寻找下一个标识符的中点
                fun nextWordRangeFrom(pos: Int): TextRange? {
                    val text = document.charsSequence
                    var i = pos.coerceIn(0, text.length)
                    while (i < text.length && !text[i].isLetterOrDigit() && text[i] != '_') i++
                    if (i >= text.length) return null
                    var s = i
                    var e = i
                    while (s > 0 && (text[s - 1].isLetterOrDigit() || text[s - 1] == '_')) s--
                    while (e < text.length && (text[e].isLetterOrDigit() || text[e] == '_')) e++
                    return if (s < e) TextRange(s, e) else null
                }
                val charsSeq = document.charsSequence
                val isOnDot = offset in 0 until charsSeq.length && charsSeq[offset] == '.'
                val isOnArrow = offset - 1 >= 0 && offset < charsSeq.length && charsSeq[offset - 1] == '-' && charsSeq[offset] == '>'
                if (isOnDot || isOnArrow) {
                    val nr = nextWordRangeFrom((offset + 1).coerceAtMost(document.textLength))
                    if (nr != null) {
                        val nmid = nr.startOffset + (nr.endOffset - nr.startOffset) / 2
                        add(nr.startOffset)
                        add(nmid)
                        add(nr.endOffset - 1)
                    }
                }
            }.distinct()

            val positions = extraOffsets.map { off ->
                val ln = document.getLineNumber(off)
                val lnStart = document.getLineStartOffset(ln)
                safeChar(ln, off - lnStart) to off
            }
            LOG.info("GTD: try positions(count=${positions.size}) first=${positions.firstOrNull()} wordRange=$wordRange")

            val targets = mutableListOf<PsiElement>()
            var firstLocation: Location? = null
            loop@ for ((lc, off) in positions) {
                val (lineNo, ch) = lc
                LOG.info("GTD: LSP definition at line=$lineNo char=$ch (absOff=$off)")
                val future = lspService.getDefinition(uri, lineNo, ch)
                val result = try { future.get(600, TimeUnit.MILLISECONDS) } catch (e: Exception) { null }
                if (result == null) continue

                if (result.isLeft) {
                    val locs = result.left ?: emptyList()
                    LOG.info("GTD: LSP locations=${locs.size}")
                    if (locs.isNotEmpty()) {
                        if (firstLocation == null) firstLocation = locs.first()
                        for (loc in locs) collectPsiTarget(project, loc, targets, leafAtCaret, wordRange)
                        if (targets.isNotEmpty()) break@loop
                    }
                } else {
                    val links = result.right ?: emptyList()
                    LOG.info("GTD: LSP locationLinks=${links.size}")
                    if (links.isNotEmpty()) {
                        for (link in links) {
                            // 优先使用 selectionRange 精确定位到方法/标识符名称
                            val range = link.targetSelectionRange ?: link.targetRange
                            val loc = Location(link.targetUri, range)
                            if (firstLocation == null) firstLocation = loc
                            collectPsiTarget(project, loc, targets, leafAtCaret, wordRange)
                        }
                        if (targets.isNotEmpty()) break@loop
                    }
                }
            }
            LOG.info("GTD: collectedTargets=${targets.size}")
            // 如果 LSP 有返回但未能构造 PSI 导航，退化为基于路径+行列的直接导航，保证功能
            if (targets.isEmpty() && firstLocation != null) {
                val loc = firstLocation!!
                val uriStr = loc.uri
                val line = loc.range.start.line.coerceAtLeast(0)
                val ch = loc.range.start.character.coerceAtLeast(0)
                LOG.info("GTD: fallback add path-based target uri=$uriStr line=$line char=$ch")
                val byPath = createPathNavigationElement(leafAtCaret, tryDecodeUriToPath(uriStr), wordRange, line, ch)
                targets.add(byPath)
            }

            if (targets.isEmpty()) null else targets.toTypedArray()
        } catch (e: Exception) {
            LOG.debug("LSP definition request failed", e)
            null
        }
    }

    override fun getActionText(context: DataContext): String? = null

    /**
     * 收集PSI目标元素，并创建包含正确位置信息的导航元素
     */
    private fun collectPsiTarget(project: Project, location: Location, targets: MutableList<PsiElement>, sourceElement: PsiElement, underlineRange: TextRange) {
        try {
            val targetVfs = resolveVirtualFile(location.uri)
            if (targetVfs == null) {
                LOG.info("GTD: cannot resolve VFS for uri=${location.uri}")
                val path = try {
                    if (location.uri.startsWith("file:")) java.nio.file.Paths.get(java.net.URI(location.uri)).toString() else location.uri
                } catch (_: Exception) { location.uri }
                val line = location.range.start.line.coerceAtLeast(0)
                val ch = location.range.start.character.coerceAtLeast(0)
                val navigationElement = createPathNavigationElement(sourceElement, path, underlineRange, line, ch)
                targets.add(navigationElement)
                return
            }

            LOG.info("GTD: resolvedVFS uri=${location.uri} -> ${targetVfs.path}")
            val psiFile = PsiManager.getInstance(project).findFile(targetVfs)
            val navigationElement = if (psiFile != null) {
                val doc = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(psiFile)
                val targetOffset = if (doc != null) {
                    // 使用 Document 进行行列到偏移转换，避免 CRLF 与编码差异带来的偏移错误
                    val totalLines = doc.lineCount.coerceAtLeast(1)
                    val lineIdx = location.range.start.line.coerceIn(0, totalLines - 1)
                    val lineStart = doc.getLineStartOffset(lineIdx)
                    val lineEnd = doc.getLineEndOffset(lineIdx)
                    val col = location.range.start.character.coerceAtLeast(0)
                    (lineStart + col).coerceIn(lineStart, lineEnd)
                } else {
                    toOffset(psiFile.text, location.range)
                }
                LOG.info("GTD: collect PSI target offset=$targetOffset in ${targetVfs.path}")
                createNavigationElement(sourceElement, targetVfs, targetOffset, underlineRange, null, null)
            } else {
                val line = location.range.start.line.coerceAtLeast(0)
                val ch = location.range.start.character.coerceAtLeast(0)
                LOG.info("GTD: psiFile not found for ${location.uri}, fallback to line=$line char=$ch in ${targetVfs.path}")
                createNavigationElement(sourceElement, targetVfs, null, underlineRange, line, ch)
            }
            targets.add(navigationElement)
        } catch (e: Exception) {
            LOG.debug("Failed to collect PSI target for location: ${location.uri}", e)
        }
    }
    
    /**
     * 创建一个包含正确位置信息的导航元素
     */
    private fun createNavigationElement(element: PsiElement, file: VirtualFile, offset: Int?, underlineRange: TextRange, line: Int?, column: Int?): PsiElement {
        return object : com.intellij.psi.impl.FakePsiElement() {
            override fun getParent(): PsiElement? = element.parent
            override fun getContainingFile(): com.intellij.psi.PsiFile? = element.containingFile
            override fun getTextRange(): TextRange? = underlineRange
            
            override fun navigate(requestFocus: Boolean) {
                try {
                    LOG.debug("Navigating to file=${file.path}, offset=${offset}, line=${line}, col=${column}")
                    val descriptor = if (offset != null) {
                        OpenFileDescriptor(element.project, file, offset)
                    } else {
                        OpenFileDescriptor(element.project, file, (line ?: 0), (column ?: 0))
                    }
                    if (descriptor.canNavigate()) {
                        descriptor.navigate(requestFocus)
                    } else {
                        LOG.warn("Cannot navigate to ${file.path}:$offset")
                        // 降级到基本的文件打开
                        val basicDescriptor = OpenFileDescriptor(element.project, file)
                        basicDescriptor.navigate(requestFocus)
                    }
                } catch (e: Exception) {
                    LOG.error("Failed to navigate", e)
                }
            }
            
            override fun canNavigate(): Boolean = true
            override fun canNavigateToSource(): Boolean = true
            override fun getTextOffset(): Int = element.textOffset
            override fun getTextLength(): Int = element.textLength
            override fun getText(): String = element.text ?: ""
            override fun toString(): String = element.toString()
        }
    }

    private fun createPathNavigationElement(element: PsiElement, filePath: String, underlineRange: TextRange, line: Int, column: Int): PsiElement {
        return object : com.intellij.psi.impl.FakePsiElement() {
            override fun getParent(): PsiElement? = element.parent
            override fun getContainingFile(): com.intellij.psi.PsiFile? = element.containingFile
            override fun getTextRange(): TextRange? = underlineRange

            override fun navigate(requestFocus: Boolean) {
                try {
                    val fs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                    val vFile = fs.refreshAndFindFileByPath(filePath)
                    if (vFile != null) {
                        val descriptor = OpenFileDescriptor(element.project, vFile, line, column)
                        if (descriptor.canNavigate()) {
                            descriptor.navigate(requestFocus)
                            return
                        }
                    }
                    LOG.warn("Cannot navigate by path: $filePath:$line:$column")
                } catch (e: Exception) {
                    LOG.error("Failed to navigate by path", e)
                }
            }

            override fun canNavigate(): Boolean = true
            override fun canNavigateToSource(): Boolean = true
            override fun getTextOffset(): Int = element.textOffset
            override fun getText(): String = element.text ?: ""
            override fun toString(): String = element.toString()
        }
    }

    private fun resolveVirtualFile(uri: String): VirtualFile? {
        return try {
            // 优先使用 VirtualFileManager 解析标准 file:// URL
            val vfm = com.intellij.openapi.vfs.VirtualFileManager.getInstance()
            vfm.findFileByUrl(uri)
                ?: run {
                    if (uri.startsWith("file:")) {
                        val u = java.net.URI(uri)
                        val path = java.nio.file.Paths.get(u).toString()
                        com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path)
                    } else if (uri.startsWith("/")) {
                        com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(uri)
                    } else {
                        // 处理可能的 URL 编码
                        val decoded = try { java.net.URLDecoder.decode(uri, Charsets.UTF_8.name()) } catch (_: Exception) { uri }
                        if (decoded.startsWith("/")) com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(decoded) else null
                    }
                }
        } catch (e: Exception) {
            LOG.debug("resolveVirtualFile failed for uri=$uri", e)
            null
        }
    }

    private fun tryDecodeUriToPath(uri: String): String {
        return try {
            if (uri.startsWith("file:")) java.nio.file.Paths.get(java.net.URI(uri)).toString() else java.net.URLDecoder.decode(uri, Charsets.UTF_8.name())
        } catch (_: Exception) { uri }
    }
    
    /**
     * 创建一个基本的导航元素（当无法找到精确元素时使用）
     */
    private fun createBasicNavigationElement(psiFile: com.intellij.psi.PsiFile, file: VirtualFile, offset: Int): PsiElement {
        return object : com.intellij.psi.impl.FakePsiElement() {
            override fun getParent(): PsiElement? = psiFile
            override fun getContainingFile(): com.intellij.psi.PsiFile? = psiFile
            
            override fun navigate(requestFocus: Boolean) {
                try {
                    LOG.debug("Navigating to file=${file.path}, offset=$offset")
                    
                    // 直接同步跳转，无任何延迟
                    val descriptor = OpenFileDescriptor(psiFile.project, file, offset)
                    if (descriptor.canNavigate()) {
                        descriptor.navigate(requestFocus)
                    } else {
                        LOG.warn("Cannot navigate to ${file.path}:$offset")
                        // 降级到基本的文件打开
                        val basicDescriptor = OpenFileDescriptor(psiFile.project, file)
                        basicDescriptor.navigate(requestFocus)
                    }
                } catch (e: Exception) {
                    LOG.error("Failed to navigate", e)
                }
            }
            
            override fun canNavigate(): Boolean = true
            override fun canNavigateToSource(): Boolean = true
            override fun getTextOffset(): Int = offset
            override fun getText(): String = ""
        }
    }

    private fun toOffset(text: String, range: org.eclipse.lsp4j.Range): Int {
        if (text.isEmpty()) return 0
        
        val lines = text.split("\n")
        val line = range.start.line.coerceIn(0, lines.size - 1)
        val col = range.start.character.coerceAtLeast(0)
        
        var offset = 0
        // 计算到目标行的偏移量
        for (i in 0 until line) {
            if (i < lines.size) {
                offset += lines[i].length + 1 // +1 是换行符
            }
        }
        
        // 加上列偏移量，但不超过当前行的长度
        if (line < lines.size && lines.isNotEmpty()) {
            val currentLine = lines[line]
            offset += col.coerceAtMost(currentLine.length)
        }
        
        val finalOffset = offset.coerceIn(0, text.length)
        LOG.debug("toOffset: line=$line, col=$col -> offset=$finalOffset (text.length=${text.length}, lines.size=${lines.size})")
        return finalOffset
    }

    /**
     * 计算光标处的“单词”范围（[A-Za-z0-9_]+）。若不存在则返回 null。
     * 用于在不支持 GotoDeclarationHandler2 的平台上，对下划线触发范围进行前置约束。
     */
    private fun computeWordRange(editor: Editor, caretOffset: Int): TextRange? {
        val chars = editor.document.charsSequence
        if (chars.isEmpty()) return null
        val length = chars.length
        val idx = caretOffset.coerceIn(0, length)

        fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

        var start = idx
        var end = idx
        while (start > 0 && isWordChar(chars[start - 1])) start--
        while (end < length && isWordChar(chars[end])) end++

        if (start >= end) return null
        val range = TextRange(start, end)
        LOG.debug("computeWordRange: caret=$caretOffset -> $range, text='${chars.subSequence(start, end)}'")
        return range
    }
}