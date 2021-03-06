package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.actionSystem.{PlatformDataKeys, LangDataKeys, AnActionEvent, AnAction}
import lang.psi.api.ScalaFile
import com.intellij.execution._
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.ui.Messages
import com.intellij.util.ActionRunner
import worksheet.runconfiguration.{WorksheetRunConfigurationFactory, WorksheetRunConfiguration, WorksheetConfigurationType}
import com.intellij.icons.AllIcons
import com.intellij.openapi.keymap.{KeymapUtil, KeymapManager}

/**
 * @author Ksenia.Sautina
 * @since 10/17/12
 */

class RunWorksheetAction extends AnAction {
  def actionPerformed(e: AnActionEvent) {
    val dataContext = e.getDataContext
    val file = LangDataKeys.PSI_FILE.getData(dataContext)
    val project = PlatformDataKeys.PROJECT.getData(dataContext)
    if (file == null || project == null) return
    file match {
      case file: ScalaFile => {
        if (!file.isWorksheetFile) return
        val runManagerEx = RunManagerEx.getInstanceEx(file.getProject)
        val configurationType = ConfigurationTypeUtil.findConfigurationType(classOf[WorksheetConfigurationType])
        val settings = runManagerEx.getConfigurationSettings(configurationType)

        def execute(setting: RunnerAndConfigurationSettings) {
          val configuration = setting.getConfiguration.asInstanceOf[WorksheetRunConfiguration]
          configuration.setWorksheetField(file.getContainingFile.getVirtualFile.getCanonicalPath)
          configuration.setName("WS: " + file.getName)
          runManagerEx.setSelectedConfiguration(setting)
          val runExecutor = DefaultRunExecutor.getRunExecutorInstance
          val runner = RunnerRegistry.getInstance().getRunner(runExecutor.getId, configuration)
          if (runner != null) {
            try {
              runner.execute(runExecutor, new ExecutionEnvironment(runner, setting, project))
            }
            catch {
              case e: ExecutionException =>
                Messages.showErrorDialog(file.getProject, e.getMessage, ExecutionBundle.message("error.common.title"))
            }
          }
        }
        for (setting <- settings) {
          ActionRunner.runInsideReadAction(new ActionRunner.InterruptibleRunnable {
            def run() {
              execute(setting)
            }
          })
          return
        }
        ActionRunner.runInsideReadAction(new ActionRunner.InterruptibleRunnable {
          def run() {
            val factory: WorksheetRunConfigurationFactory =
              configurationType.getConfigurationFactories.apply(0).asInstanceOf[WorksheetRunConfigurationFactory]
            val setting = RunManagerEx.getInstanceEx(file.getProject).createConfiguration(file.getName, factory)

            runManagerEx.setTemporaryConfiguration(setting)
            execute(setting)
          }
        })
      }
      case _ =>
    }
  }

  override def update(e: AnActionEvent) {
    val presentation = e.getPresentation
    presentation.setIcon(AllIcons.Actions.Execute)
    val shortcuts = KeymapManager.getInstance.getActiveKeymap.getShortcuts("Scala.RunWorksheet")
    if (shortcuts.length > 0) {
      val shortcutText = " (" + KeymapUtil.getShortcutText(shortcuts(0)) + ")"
      presentation.setText(ScalaBundle.message("worksheet.execute.button") + shortcutText)
    }

    def enable() {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }
    def disable() {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }
    try {
      val file = LangDataKeys.PSI_FILE.getData(e.getDataContext)
      file match {
        case sf: ScalaFile => {
          if (sf.isWorksheetFile) enable()
          else disable()
        }
        case _ => disable()
      }
    }
    catch {
      case e: Exception => disable()
    }
  }
}