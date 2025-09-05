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
            
            // 大幅延迟，确保IDE组件完全加载并达到COMPONENTS_LOADED状态
            delay(3000)
            
            // 检查应用程序状态
            val app = ApplicationManager.getApplication()
            if (app == null || app.isDisposed) {
                LOG.warn("Application not fully initialized, skipping LSP startup")
                return
            }
            
            // 检查项目是否包含 .zy 文件
            if (hasZyFiles(project)) {
                LOG.info("Found .zy files in project: ${project.name}, starting LSP service")
                
                // 再次延迟一下，确保所有组件都已准备好
                delay(1000)
                
                // 在EDT线程中安全地启动LSP服务
                ApplicationManager.getApplication().invokeLater {
                    try {
                        val lspService = project.getService(ZyLspService::class.java)
                        if (lspService != null && !project.isDisposed) {
                            val started = lspService.startLspService()
                            if (started) {
                                LOG.info("LSP service started successfully for project: ${project.name}")
                            } else {
                                LOG.warn("Failed to start LSP service for project: ${project.name}")
                            }
                        } else {
                            LOG.error("ZyLspService not found or project disposed for project: ${project.name}")
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