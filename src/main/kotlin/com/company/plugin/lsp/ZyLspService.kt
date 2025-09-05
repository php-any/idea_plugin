package com.company.plugin.lsp

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

/**
 * ZY LSP 服务管理器
 * 管理 LSP 客户端的生命周期
 */
@Service(Service.Level.PROJECT)
class ZyLspService(private val project: Project) {
    
    companion object {
        private val LOG = Logger.getInstance(ZyLspService::class.java)
    }
    
    private var lspClient: ZyLspClient? = null
    private var isStarted = false
    
    /**
     * 启动 LSP 服务
     */
    fun startLspService(): Boolean {
        if (isStarted) {
            LOG.debug("LSP service already started for project: ${project.name}")
            return true
        }
        
        try {
            lspClient = ZyLspClient(project)
            val success = lspClient!!.startServer()
            
            if (success) {
                isStarted = true
                LOG.info("LSP service started successfully for project: ${project.name}")
            } else {
                lspClient = null
                LOG.warn("Failed to start LSP service for project: ${project.name}")
            }
            
            return success
        } catch (e: Exception) {
            LOG.error("Error starting LSP service", e)
            lspClient = null
            return false
        }
    }
    
    /**
     * 停止 LSP 服务
     */
    fun stopLspService() {
        if (!isStarted) {
            return
        }
        
        try {
            lspClient?.stopServer()
            lspClient = null
            isStarted = false
            LOG.info("LSP service stopped for project: ${project.name}")
        } catch (e: Exception) {
            LOG.error("Error stopping LSP service", e)
        }
    }
    
    /**
     * 获取代码补全
     */
    fun getCompletion(uri: String, line: Int, character: Int): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return lspClient?.getCompletion(uri, line, character) 
            ?: CompletableFuture.completedFuture(Either.forLeft(emptyList()))
    }

    /**
     * 获取定义位置
     */
    fun getDefinition(uri: String, line: Int, character: Int): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        return lspClient?.getDefinition(uri, line, character)
            ?: CompletableFuture.completedFuture(Either.forLeft(emptyList()))
    }

    /**
     * 确保文档 didOpen 同步
     */
    fun ensureDidOpen(uri: String, languageId: String, text: String) {
        lspClient?.ensureDidOpen(uri, languageId, text)
    }
    
    /**
     * 检查 LSP 服务是否已启动
     */
    fun isStarted(): Boolean = isStarted
}