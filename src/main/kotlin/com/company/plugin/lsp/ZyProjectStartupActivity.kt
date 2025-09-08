package com.company.plugin.lsp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.delay
import java.io.File

/**
 * ZY 项目启动活动
 * 在项目启动时检查是否包含 .zy 文件，如果包含则启动 LSP 服务
 */
class ZyProjectStartupActivity : ProjectActivity {
    
    companion object {
        private val LOG = Logger.getInstance(ZyProjectStartupActivity::class.java)
    }
    
    override suspend fun execute(project: Project) {
        try {
            LOG.info("ZyProjectStartupActivity executed for project: ${project.name}")
            
            // 检查IDE是否已完全初始化到COMPONENTS_LOADED状态
            val app = ApplicationManager.getApplication()
            if (app == null || app.isDisposed) {
                LOG.warn("Application not ready, skipping LSP startup")
                return
            }
            
            // 等待IDE完全加载
            while (!com.intellij.diagnostic.LoadingState.COMPONENTS_LOADED.isOccurred) {
                LOG.debug("Waiting for COMPONENTS_LOADED state...")
                delay(100)
            }
            
            // 额外延迟确保所有组件稳定
            delay(1000)
            
            // 检查项目是否包含 .zy 文件
            if (hasZyFiles(project)) {
                LOG.info("Found .zy files in project: ${project.name}, starting LSP service")
                
                // 在EDT线程中安全地启动LSP服务
                ApplicationManager.getApplication().invokeLater {
                    try {
                        if (project.isDisposed) {
                            LOG.debug("Project disposed, skipping LSP startup")
                            return@invokeLater
                        }
                        
                        val lspService = project.getService(ZyLspService::class.java)
                        if (lspService != null) {
                            val started = lspService.startLspService()
                            if (started) {
                                LOG.info("LSP service started successfully for project: ${project.name}")
                            } else {
                                LOG.warn("Failed to start LSP service for project: ${project.name}")
                            }
                        } else {
                            LOG.error("ZyLspService not found for project: ${project.name}")
                        }
                    } catch (e: Exception) {
                        LOG.error("Error starting LSP service for project: ${project.name}", e)
                    }
                }
            } else {
                LOG.debug("No .zy files found in project: ${project.name}, skipping LSP startup")
            }
        } catch (e: Exception) {
            LOG.error("Error in ZyProjectStartupActivity for project: ${project.name}", e)
        }
    }
    
    /**
     * 检查项目是否包含 .zy 文件
     */
    private fun hasZyFiles(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        
        try {
            val projectDir = File(basePath)
            if (!projectDir.exists() || !projectDir.isDirectory) {
                LOG.debug("Project base path does not exist or is not a directory: $basePath")
                return false
            }
            
            LOG.debug("Scanning for .zy files in: $basePath")
            
            return projectDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".zy") }
                .any().also { found ->
                    LOG.debug("Found .zy files: $found in project: ${project.name}")
                }
                
        } catch (e: Exception) {
            LOG.error("Error scanning for .zy files in project: ${project.name}", e)
            return false
        }
    }
}