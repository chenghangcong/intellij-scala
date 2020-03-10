package org.jetbrains.plugins.scala
package project
package notification

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.ui.{EditorNotificationPanel, EditorNotifications}
import org.jetbrains.sbt.Sbt

/**
 * @author adkozlov
 */
abstract class AbstractNotificationProvider(kitTitle: String,
                                            project: Project)
  extends EditorNotifications.Provider[EditorNotificationPanel] {

  import AbstractNotificationProvider._

  override final val getKey: Key[EditorNotificationPanel] = Key.create(kitTitle)

  {
    val notifications = EditorNotifications.getInstance(project)
    project.subscribeToModuleRootChanged() { _ =>
      notifications.updateAllNotifications()
    }
  }

  protected def panelText(kitTitle: String): String

  protected def hasDeveloperKit(module: Module): Boolean

  protected def setDeveloperKit(module: Module): Unit

  override final def createNotificationPanel(virtualFile: VirtualFile,
                                             fileEditor: FileEditor): EditorNotificationPanel =
    PsiManager.getInstance(project).findFile(virtualFile) match {
      case file: PsiFile if isSourceCode(file) =>
        ModuleUtilCore.findModuleForPsiElement(file) match {
          case module: Module if !hasDeveloperKit(module) => createPanel(() => setDeveloperKit(module))
          case _ => null
        }
      case _ => null
    }

  private def createPanel(action: Runnable) = {
    val panel = new EditorNotificationPanel()
      .text(panelText(kitTitle))
    panel.createActionLabel("Setup " + kitTitle, action)
    panel
  }
}

object AbstractNotificationProvider {

  private def isSourceCode(file: PsiFile): Boolean =
    file.getLanguage.isKindOf(ScalaLanguage.INSTANCE) &&
      !file.getName.endsWith(Sbt.Extension) && // root sbt files belong to main (not *-build) modules
      file.isWritable
}
