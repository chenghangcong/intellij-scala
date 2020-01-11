package org.jetbrains.plugins.scala.project.sdkdetect

import java.nio.file.Path
import java.util.stream.{Stream => JStream}

import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.project.template.{PathExt, _}

class ProjectLocalDetector(contextDirectory: VirtualFile) extends ScalaSdkDetector {
  override def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = ProjectSdkChoice(descriptor)
  override def friendlyName: String = "Local project libraries"

  override def buildJarStream: JStream[Path] = {
    if (contextDirectory == null)
      return JStream.empty()

    VfsUtilCore.virtualToIoFile(contextDirectory).toOption
      .map(_.toPath / "lib")
      .filter(_.exists)
      .map(collectJarFiles)
      .getOrElse(JStream.empty())
  }
}

