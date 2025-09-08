package com.company.plugin.navigation

import com.company.plugin.lsp.ZyLspService
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Range
import java.util.concurrent.TimeUnit

/**
 * ZY 跳转到定义处理器
 * 使用 LSP definition 能力实现从符号到定义位置的跳转
 */
class ZyGotoDeclarationHandler : GotoDeclarationHandler {

    companion object {
        private val LOG = Logger.getInstance(ZyGotoDeclarationHandler::class.java)
    }

    override fun getGotoDeclarationTargets(element: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val project: Project = editor.project ?: return null
        val file: VirtualFile = element?.containingFile?.virtualFile ?: return null
        if (!file.name.endsWith(".zy")) return null

        // 移除所有去重机制，直接处理每次请求

        return try {
            LOG.debug("GotoDeclaration triggered for file=${file.path}, offset=$offset")
            val lspService = project.getService(ZyLspService::class.java) ?: return null
            if (!lspService.isStarted()) {
                LOG.debug("LSP service not started")
                return null
            }

            val document = editor.document
            val lineNumber = document.getLineNumber(offset)
            val lineStartOffset = document.getLineStartOffset(lineNumber)
            val character = offset - lineStartOffset
            val uri = file.url
            
            // 直接同步文档，不做任何优化判断
            val text = document.text
            lspService.ensureDidOpen(uri, "zy", text)
            lspService.notifyDocumentChange(uri, text)

            LOG.debug("Requesting definition for uri=$uri, line=$lineNumber, char=$character")
            val future = lspService.getDefinition(uri, lineNumber, character)
            val result = future.get(500, TimeUnit.MILLISECONDS) // 进一步减少超时时间

            val targets = mutableListOf<PsiElement>()

            // 只收集目标，不直接跳转，让IDE的标准机制处理跳转
            if (result.isLeft) {
                val locs = result.left ?: emptyList()
                LOG.debug("Definition returned ${locs.size} locations")
                for (loc in locs) {
                    collectPsiTarget(project, loc, targets)
                }
            } else {
                val links = result.right ?: emptyList()
                LOG.debug("Definition returned ${links.size} locationLinks")
                for (link in links) {
                    val loc = Location(link.targetUri, link.targetRange)
                    collectPsiTarget(project, loc, targets)
                }
            }

            if (targets.isEmpty()) null else targets.toTypedArray()
        } catch (e: Exception) {
            LOG.debug("LSP definition request failed", e)
            null
        }
    }

    override fun getActionText(context: com.intellij.openapi.actionSystem.DataContext): String? = null

    /**
     * 收集PSI目标元素，并创建包含正确位置信息的导航元素
     */
    private fun collectPsiTarget(project: Project, location: Location, targets: MutableList<PsiElement>) {
        try {
            val targetVfs = com.intellij.openapi.vfs.VfsUtil.findFileByURL(java.net.URL(location.uri))
                ?: return
            val psiFile = PsiManager.getInstance(project).findFile(targetVfs) ?: return
            val targetOffset = toOffset(psiFile.text, location.range)
            
            LOG.debug("Collecting PSI target uri=${location.uri}, offset=$targetOffset")
            
            // 创建一个包含正确位置信息的导航元素
            val targetElement = psiFile.findElementAt(targetOffset)
            if (targetElement != null) {
                // 使用自定义的导航元素来确保正确的位置
                val navigationElement = createNavigationElement(targetElement, targetVfs, targetOffset)
                targets.add(navigationElement)
            }
        } catch (e: Exception) {
            LOG.debug("Failed to collect PSI target for location: ${location.uri}", e)
        }
    }
    
    /**
     * 创建一个包含正确位置信息的导航元素
     */
    private fun createNavigationElement(element: PsiElement, file: VirtualFile, offset: Int): PsiElement {
        return object : com.intellij.psi.impl.FakePsiElement() {
            override fun getParent(): PsiElement? = element.parent
            override fun getContainingFile(): com.intellij.psi.PsiFile? = element.containingFile
            
            override fun navigate(requestFocus: Boolean) {
                try {
                    LOG.debug("Navigating to file=${file.path}, offset=$offset")
                    
                    // 直接同步跳转，无任何延迟
                    val descriptor = OpenFileDescriptor(element.project, file, offset)
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
            override fun getTextOffset(): Int = offset
            override fun getText(): String = element.text ?: ""
        }
    }

    private fun toOffset(text: String, range: Range): Int {
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
}


