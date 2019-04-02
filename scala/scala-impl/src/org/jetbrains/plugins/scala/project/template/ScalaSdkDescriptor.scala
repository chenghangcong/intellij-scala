package org.jetbrains.plugins.scala
package project
package template

import java.io.File

/**
 * @author Pavel Fatin
 */
final case class ScalaSdkDescriptor(maybeVersion: Option[Version],
                                    compilerClasspath: Seq[File],
                                    libraryFiles: Seq[File],
                                    sourceFiles: Seq[File],
                                    docFiles: Seq[File])
  extends Ordered[ScalaSdkDescriptor] {

  override def compare(that: ScalaSdkDescriptor): Int = that.maybeVersion.compare(maybeVersion)
}

object ScalaSdkDescriptor {

  import Artifact._
  import Kind._

  def from(components: Seq[Component]): Either[String, ScalaSdkDescriptor] = {
    val componentsByKind = components.groupBy(_.kind)
      .withDefault(Function.const(Seq.empty))

    def filesByKind(kind: Kind) =
      files(componentsByKind(kind))()

    val binaryComponents = componentsByKind(Binaries)

    requiredBinaryArtifacts -- binaryComponents.map(_.artifact) match {
      case missingBinaryArtifacts if missingBinaryArtifacts.nonEmpty =>
        Left("Not found: " + missingBinaryArtifacts.map(_.prefix + "*.jar").mkString(", "))
      case _ =>
        val libraryVersion = binaryComponents.collectFirst {
          case Component(ScalaLibrary, _, Some(version), _) => version
        }

        val descriptor = ScalaSdkDescriptor(
          libraryVersion,
          files(binaryComponents)(requiredBinaryArtifacts),
          files(binaryComponents)(),
          filesByKind(Sources),
          filesByKind(Docs)
        )

        Right(descriptor)
    }
  }

  implicit class DescriptorExt(private val descriptor: ScalaSdkDescriptor) extends AnyVal {

    def versionText(default: String = "Unknown")
                   (presentation: Version => String = _.presentation): String =
      descriptor.maybeVersion.fold(default)(presentation)
  }

  private[this] def requiredBinaryArtifacts = Set[Artifact](
    ScalaLibrary,
    ScalaCompiler,
    ScalaReflect
  )

  private[this] def files(components: Seq[Component])
                         (predicate: Artifact => Boolean = ScalaArtifacts - ScalaCompiler) =
    for {
      Component(artifact, _, _, file) <- components
      if predicate(artifact)
    } yield file
}