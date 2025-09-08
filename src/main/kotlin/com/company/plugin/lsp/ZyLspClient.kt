package com.company.plugin.lsp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * ZY LSP 客户端
 * 基于 LSP4J 实现，通过 stdio 协议与 zy-lsp 通信
 */
class ZyLspClient(private val project: Project) : LanguageClient {
    
    companion object {
        private val LOG = Logger.getInstance(ZyLspClient::class.java)
    }
    
    private var languageServer: LanguageServer? = null
    private var launcher: Launcher<LanguageServer>? = null
    private var process: Process? = null
    private var isInitialized = false
    private var serverCapabilities: ServerCapabilities? = null
    // 已同步到 LSP 的文档 URI 集合，用于避免重复 didOpen
    private val openedDocuments = mutableSetOf<String>()
    // 文档版本号，用于跟踪文档变更
    private val documentVersions = mutableMapOf<String, Int>()
    
    /**
     * 检查进程状态，确保 LSP 服务器仍在运行
     */
    private fun isProcessAlive(): Boolean {
        return try {
            process?.isAlive ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 清理状态并重置初始化标志
     */
    private fun cleanupState() {
        isInitialized = false
        openedDocuments.clear()
        documentVersions.clear()
        serverCapabilities = null
    }
    
    /**
     * 启动 LSP 服务器
     */
    fun startServer(): Boolean {
        try {
            // 检查IDE是否已达到COMPONENTS_LOADED状态
            if (!com.intellij.diagnostic.LoadingState.COMPONENTS_LOADED.isOccurred) {
                LOG.warn("IDE not fully loaded (COMPONENTS_LOADED), cannot start LSP server")
                return false
            }
            
            // 检查应用程序是否已完全初始化
            val app = com.intellij.openapi.application.ApplicationManager.getApplication()
            if (app == null || app.isDisposed) {
                LOG.warn("Application not ready for LSP startup")
                return false
            }
            
            val lspExecutable = resolveLspExecutable()
            if (lspExecutable == null) {
                LOG.warn("zy-lsp executable not found")
                return false
            }
            LOG.info("Resolved zy-lsp executable: $lspExecutable")
            LOG.info("Env PATH: ${System.getenv("PATH")}")
            LOG.info("Env ZY_ROOT: ${System.getenv("ZY_ROOT")}")
            
            val logDir = File(project.basePath, ".idea/zy-lsp-logs")
            logDir.mkdirs()
            val logFile = File(logDir, "zy-lsp.log")
            LOG.info("zy-lsp server log file: ${logFile.absolutePath}")
            
            val processBuilder = ProcessBuilder(
                lspExecutable,
                "-log-file", logFile.absolutePath,
                "-log-level", "5"
            )
            
            val workDir = File(project.basePath ?: ".")
            processBuilder.directory(workDir)
            LOG.info("Starting zy-lsp, workDir=${workDir.absolutePath}")
            process = processBuilder.start()
            
            // 创建 LSP 启动器
            launcher = LSPLauncher.createClientLauncher(
                this,
                process!!.inputStream,
                process!!.outputStream
            )
            
            languageServer = launcher!!.remoteProxy
            
            // 启动通信
            launcher!!.startListening()
            
            // 初始化服务器
            val initParams = InitializeParams().apply {
                processId = ProcessHandle.current().pid().toInt()
                workspaceFolders = listOf(
                    WorkspaceFolder("file://${project.basePath}", project.name)
                )
                capabilities = ClientCapabilities().apply {
                    textDocument = TextDocumentClientCapabilities().apply {
                        completion = CompletionCapabilities().apply {
                            completionItem = CompletionItemCapabilities().apply {
                                snippetSupport = true
                            }
                        }
                    }
                }
            }
            
            val initResult = languageServer!!.initialize(initParams).get()
            serverCapabilities = initResult.capabilities
            languageServer!!.initialized(InitializedParams())
            
            isInitialized = true
            LOG.info("LSP server initialized successfully for project: ${project.name}")
            LOG.info("Server capabilities: definition=${serverCapabilities?.definitionProvider}, completion=${serverCapabilities?.completionProvider != null}")
            
            return true
            
        } catch (e: Exception) {
            LOG.error("Failed to start LSP server", e)
            cleanupState() // 清理状态
            return false
        }
    }
    
    /**
     * 停止 LSP 服务器
     */
    fun stopServer() {
        try {
            cleanupState() // 先清理状态
            languageServer?.shutdown()?.get()
            languageServer?.exit()
            process?.destroyForcibly()
            
            languageServer = null
            launcher = null
            process = null
            
            LOG.info("LSP server stopped for project: ${project.name}")
        } catch (e: Exception) {
            LOG.error("Error stopping LSP server", e)
        }
    }
    
    /**
     * 获取代码补全
     */
    fun getCompletion(uri: String, line: Int, character: Int): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        if (!isInitialized || languageServer == null || !isProcessAlive()) {
            if (!isProcessAlive()) {
                LOG.warn("LSP process is dead, cleaning up state")
                cleanupState()
            }
            return CompletableFuture.completedFuture(Either.forLeft(emptyList()))
        }
        
        val params = CompletionParams().apply {
            textDocument = TextDocumentIdentifier(uri)
            position = Position(line, character)
        }
        
        return languageServer!!.textDocumentService.completion(params)
    }

    /**
     * 获取定义位置（Go to Definition）
     */
    fun getDefinition(uri: String, line: Int, character: Int): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        if (!isInitialized || languageServer == null || !isProcessAlive()) {
            if (!isProcessAlive()) {
                LOG.warn("LSP process is dead, cleaning up state")
                cleanupState()
            }
            return CompletableFuture.completedFuture(Either.forLeft(emptyList()))
        }
        LOG.debug("Request definition uri=$uri line=$line char=$character")
        val params = DefinitionParams(TextDocumentIdentifier(uri), Position(line, character))
        val future = languageServer!!.textDocumentService.definition(params)
        // 附加一个监听仅用于日志
        future.thenAccept { either ->
            try {
                val leftSize = either?.left?.size ?: 0
                val rightSize = either?.right?.size ?: 0
                LOG.debug("Definition response: locations=$leftSize, links=$rightSize")
            } catch (_: Throwable) {}
        }
        return future
    }

    /**
     * 检查文档是否已经在LSP中打开
     */
    fun isDocumentOpen(uri: String): Boolean {
        return openedDocuments.contains(uri)
    }
    
    /**
     * 确保 LSP 端已打开并同步指定文档（最小实现：仅 didOpen 一次）
     */
    fun ensureDidOpen(uri: String, languageId: String, text: String) {
        if (!isInitialized || languageServer == null || !isProcessAlive()) {
            if (!isProcessAlive()) {
                LOG.warn("LSP process is dead, cannot send didOpen")
                cleanupState()
            }
            return
        }
        if (openedDocuments.contains(uri)) return
        try {
            val version = documentVersions.getOrPut(uri) { 1 }
            val item = TextDocumentItem(uri, languageId, version, text)
            languageServer!!.textDocumentService.didOpen(DidOpenTextDocumentParams(item))
            openedDocuments.add(uri)
            LOG.debug("didOpen sent for uri=$uri, version=$version, bytes=${text.toByteArray().size}")
        } catch (e: Exception) {
            LOG.debug("Failed to send didOpen for uri=$uri", e)
        }
    }
    
    /**
     * 通知 LSP 服务器文档内容变更
     */
    fun didChange(uri: String, newText: String) {
        if (!isInitialized || languageServer == null || !isProcessAlive()) {
            if (!isProcessAlive()) {
                LOG.warn("LSP process is dead, cannot send didChange")
                cleanupState()
            }
            return
        }
        if (!openedDocuments.contains(uri)) {
            LOG.debug("Document not opened, skipping didChange for uri=$uri")
            return
        }
        
        try {
            val version = documentVersions.getOrPut(uri) { 1 }
            documentVersions[uri] = version + 1
            
            // 使用全量变更（简单实现）
            val changes = listOf(TextDocumentContentChangeEvent(newText))
            
            val params = DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, documentVersions[uri]!!),
                changes
            )
            
            languageServer!!.textDocumentService.didChange(params)
            LOG.debug("didChange sent for uri=$uri, version=${documentVersions[uri]}")
        } catch (e: Exception) {
            LOG.debug("Failed to send didChange for uri=$uri", e)
        }
    }
    
    /**
     * 解析 LSP 可执行文件路径
     */
    private fun resolveLspExecutable(): String? {
        // 常见的 zy-lsp 安装路径
        val paths = listOf(
            "/usr/local/bin/zy-lsp",
            "/opt/homebrew/bin/zy-lsp",
            "${System.getProperty("user.home")}/.cargo/bin/zy-lsp",
            "${System.getProperty("user.home")}/go/bin/zy-lsp",
            "zy-lsp" // 系统 PATH 中
        )
        
        // 检查 ZY_ROOT 环境变量
        System.getenv("ZY_ROOT")?.let { zyRoot ->
            val zyLspPath = "$zyRoot/bin/zy-lsp"
            if (File(zyLspPath).exists()) {
                return zyLspPath
            }
        }
        
        // 检查常见路径
        for (path in paths) {
            if (File(path).exists() || isInPath(path)) {
                return path
            }
        }
        
        return null
    }
    
    /**
     * 检查命令是否在 PATH 中
     */
    private fun isInPath(command: String): Boolean {
        return try {
            val process = ProcessBuilder("which", command).start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    // LanguageClient 接口方法
    override fun telemetryEvent(obj: Any?) {
        // 遥测事件处理
    }
    
    override fun publishDiagnostics(params: PublishDiagnosticsParams?) {
        // 诊断信息处理
    }
    
    override fun showMessage(params: MessageParams?) {
        params?.let {
            LOG.info("LSP Message: $params")
        }
    }
    
    override fun showMessageRequest(params: ShowMessageRequestParams?): CompletableFuture<MessageActionItem?> {
        return CompletableFuture.completedFuture(null)
    }
    
    override fun logMessage(params: MessageParams?) {
        params?.let {
            LOG.debug("LSP Log: $params")
        }
    }
}