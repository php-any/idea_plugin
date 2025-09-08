package com.company.plugin.lsp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * ZY 文档同步组件
 * 监听文档变更并通知 LSP 服务器
 */
class ZyDocumentSyncComponent : ProjectActivity {
    
    companion object {
        private val LOG = Logger.getInstance(ZyDocumentSyncComponent::class.java)
    }
    
    override suspend fun execute(project: Project) {
        // 添加全局文档监听器
        val documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                handleDocumentChange(project, event)
            }
        }
        
        // 使用 EditorFactory 添加全局监听器
        com.intellij.openapi.editor.EditorFactory.getInstance()
            .eventMulticaster.addDocumentListener(documentListener, project)
        
        LOG.info("ZY document sync component initialized for project: ${project.name}")
    }
    
    private fun handleDocumentChange(project: Project, event: DocumentEvent) {
        try {
            val document = event.document
            val file = FileDocumentManager.getInstance().getFile(document)
            
            // 只处理 .zy 文件
            if (file == null || !file.name.endsWith(".zy")) {
                return
            }
            
            val lspService = project.getService(ZyLspService::class.java)
            if (lspService == null || !lspService.isStarted()) {
                return
            }
            
            // 通知 LSP 服务器文档变更
            val uri = file.url
            val newText = document.text
            lspService.notifyDocumentChange(uri, newText)
            
            LOG.debug("Document change notified to LSP: ${file.name}")
            
        } catch (e: Exception) {
            LOG.debug("Error handling document change", e)
        }
    }
}