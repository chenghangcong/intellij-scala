package org.jetbrains.plugins.scala.project.sdkdetect

import java.nio.file.{Path, Paths}
import java.util.function.{Function => JFunction}
import java.util.stream.{Stream => JStream}

import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.scala.project.template.{PathExt, _}

object BrewDetector extends ScalaSdkDetector {
  override def buildSdkChoice(descriptor: ScalaSdkDescriptor): SdkChoice = BrewSdkChoice(descriptor)
  override def friendlyName: String = "Brew packages"

  override def buildJarStream: JStream[Path] = {
    val scalaRoot = Paths.get("/") / "usr" / "local" / "Cellar" / "scala"

    if (!SystemInfo.isMac || !scalaRoot.exists)
      return JStream.empty()

    scalaRoot.children
      .filter(f => (f / "libexec" / "lib").exists)
      .map[JStream[Path]](collectJarFiles)
      .flatMap(JFunction.identity[JStream[Path]]())

  }
}