/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.ui.DebuggerContentInfo
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.DebuggingRunnerData
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.icons.AllIcons
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.util.Key
import com.intellij.ui.content.ContentManagerAdapter
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.kotlin.psi.UserDataProperty

/**
 * Installs coroutines debug agent and coroutines tab if `kotlinx.coroutines.debug` dependency is found
 */
@Suppress("IncompatibleAPI")
class CoroutinesDebugConfigurationExtension : RunConfigurationExtension() {
    private var Project.listenerCreated: Boolean? by UserDataProperty(Key.create("COROUTINES_DEBUG_TAB_CREATE_LISTENER"))

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
        // unable to check dependencies, so let it be true, since it patches only,
        // when debug facility is found and launched in debug mode
        return true
    }

    override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
        configuration: T,
        params: JavaParameters?,
        runnerSettings: RunnerSettings?
    ) {
        if (runnerSettings is DebuggingRunnerData && params != null
            && params.classPath != null
            && params.classPath.pathList.isNotEmpty()
        ) {
            params.classPath.pathList.forEach {
                if (!it.contains("kotlinx-coroutines-debug")) return@forEach
                // if debug library is included into project, add agent which installs probes
                params.vmParametersList?.add("-javaagent:$it")
                params.vmParametersList?.add("-ea")
                val project = (configuration as RunConfigurationBase<*>).project
                // add listener to put coroutines tab into debugger tab
                if (project.listenerCreated != true) { // prevent multiple listeners creation
                    project.messageBus.connect().subscribe(
                        XDebuggerManager.TOPIC,
                        object : XDebuggerManagerListener {
                            override fun processStarted(debugProcess: XDebugProcess) {
                                val session = DebuggerManagerEx.getInstanceEx(project).context.debuggerSession
                                DebuggerInvocationUtil.swingInvokeLater(project) {
                                    registerCoroutinesPanel(session?.xDebugSession?.ui ?: return@swingInvokeLater, session)
                                }
                            }
                        })
                    project.listenerCreated = true
                    return
                }
            }

        }
    }

    /**
     * Adds panel to XDebugSessionTab
     */
    private fun registerCoroutinesPanel(ui: RunnerLayoutUi, session: DebuggerSession) {
        val panel = CoroutinesPanel(session.project, session.contextManager)
        val content = ui.createContent(
            DebuggerContentInfo.THREADS_CONTENT, panel, "Coroutines", // TODO(design)
            AllIcons.Debugger.ThreadGroup, null
        )
        content.isCloseable = false
        ui.addContent(content, 0, PlaceInGrid.left, true)
        ui.addListener(object : ContentManagerAdapter() {
            override fun selectionChanged(event: ContentManagerEvent) {
                if (event.content === content) {
                    if (content.isSelected) {
                        panel.setUpdateEnabled(true)
                        if (panel.isRefreshNeeded) {
                            panel.rebuildIfVisible(DebuggerSession.Event.CONTEXT)
                        }
                    } else {
                        panel.setUpdateEnabled(false)
                    }
                }
            }
        }, content)
    }

    @Suppress("UNUSED") // not sure will it be needed
    fun downloadDebugDependency(project: Project, version: String): List<OrderRoot>? {
        val description = JpsMavenRepositoryLibraryDescriptor("org.jetbrains.kotlinx", "kotlinx-coroutines-debug", version)
        return JarRepositoryManager.loadDependenciesSync(
            project, description, setOf(ArtifactKind.ARTIFACT),
            listOf(RemoteRepositoryDescription.MAVEN_CENTRAL), null
        )
    }
}