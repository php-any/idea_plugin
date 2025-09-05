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
            
            // 确保文档已同步到LSP
            val text = document.text
            lspService.ensureDidOpen(uri, "zy", text)

            LOG.debug("Requesting definition for uri=$uri, line=$lineNumber, char=$character")
            val future = lspService.getDefinition(uri, lineNumber, character)
            val result = future.get(1200, TimeUnit.MILLISECONDS)

            // 处理LSP响应并直接跳转
            if (result.isLeft) {
                val locs = result.left ?: emptyList()
                LOG.debug("Definition returned ${locs.size} locations")
                if (locs.isNotEmpty()) {
                    navigateToLocation(project, locs[0])
                }
            } else {
                val links = result.right ?: emptyList()
                LOG.debug("Definition returned ${links.size} locationLinks")
                if (links.isNotEmpty()) {
                    val link = links[0]
                    val loc = Location(link.targetUri, link.targetRange)
                    navigateToLocation(project, loc)
                }
            }

            // 返回空数组，因为我们已经处理了跳转
            emptyArray()
        } catch (e: Exception) {
            LOG.debug("LSP definition request failed", e)
            null
        }
    }

    override fun getActionText(context: com.intellij.openapi.actionSystem.DataContext): String? = null

    private fun navigateToLocation(project: Project, location: Location) {
        try {
            val targetVfs = com.intellij.openapi.vfs.VfsUtil.findFileByURL(java.net.URL(location.uri))
                ?: return
            val psiFile = PsiManager.getInstance(project).findFile(targetVfs) ?: return
            val targetOffset = toOffset(psiFile.text, location.range)
            
            LOG.debug("Navigating to uri=${location.uri}, offset=$targetOffset")
            
            ApplicationManager.getApplication().invokeLater {
                OpenFileDescriptor(project, targetVfs, targetOffset).navigate(true)
            }
        } catch (e: Exception) {
            LOG.error("Failed to navigate to location: ${location.uri}", e)
        }
    }

    private fun toOffset(text: String, range: Range): Int {
        val lines = text.split("\n")
        val line = range.start.line.coerceIn(0, lines.size - 1)
        val col = range.start.character.coerceAtLeast(0)
        
        var offset = 0
        // 计算到目标行的偏移量
        for (i in 0 until line) {
            offset += lines[i].length + 1 // +1 是换行符
        }
        // 加上列偏移量，但不超过当前行的长度
        if (line < lines.size) {
            offset += col.coerceAtMost(lines[line].length)
        }
        
        LOG.debug("toOffset: line=$line, col=$col -> offset=$offset (text.length=${text.length})")
        return offset.coerceIn(0, text.length)
    }
}


