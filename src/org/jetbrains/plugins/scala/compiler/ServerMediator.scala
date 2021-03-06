package org.jetbrains.plugins.scala
package compiler

import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import config.ScalaFacet
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.notification.{NotificationType, Notification, Notifications}
import com.intellij.openapi.compiler.{CompilerMessageCategory, CompileContext, CompileTask, CompilerManager}
import com.intellij.openapi.roots.ProjectRootManager
import extensions._

/**
 * Pavel Fatin
 */

class ServerMediator(project: Project) extends ProjectComponent {
  CompilerManager.getInstance(project).addBeforeTask(new CompileTask {
    var firstCompilation = true

    def execute(context: CompileContext): Boolean = {
      val scalaProject = ScalaFacet.isPresentIn(project)

      val externalCompiler = CompilerWorkspaceConfiguration.getInstance(project).USE_COMPILE_SERVER

      if (scalaProject) {
        if (externalCompiler) {
          if (firstCompilation) {
            val title = "Using a new (SBT-based) Scala compiler."
            val message = "In case of any compilation problems you may enable the previous (internal) compiler by clearing:\n" +
                    "Project Settings / Compiler / Use external build"
            Notifications.Bus.notify(new Notification("scala", title, message, NotificationType.INFORMATION))
            firstCompilation = false
          }

          invokeAndWait {
            project.getComponent(classOf[FscServerLauncher]).stop()
            project.getComponent(classOf[FscServerManager]).removeWidget()
          }

          val applicationSettings = ScalaApplicationSettings.getInstance()

          if (applicationSettings.COMPILE_SERVER_ENABLED) {
            invokeAndWait {
              CompileServerManager.instance(project).configureWidget()
            }

            if (!CompileServerLauncher.instance.running) {
              val sdk = ProjectRootManager.getInstance(project).getProjectSdk

              if (sdk == null) {
                context.addMessage(CompilerMessageCategory.ERROR, "No project SDK to run Scala compile server.\n" +
                        "Please either disable Scala compile server or specify a project SDK.", null, -1, -1)
                return false
              }

              invokeAndWait {
                CompileServerLauncher.instance.init(sdk)
              }
            }
          }
        } else {
          invokeAndWait {
            CompileServerLauncher.instance.stop()
            CompileServerManager.instance(project).removeWidget()
          }
        }
      }

      true
    }
  })

  def getComponentName = getClass.getSimpleName

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {}

  def projectClosed() {}
}