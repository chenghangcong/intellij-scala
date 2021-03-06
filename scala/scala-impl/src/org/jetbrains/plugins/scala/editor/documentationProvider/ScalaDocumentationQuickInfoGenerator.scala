package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{ElementText, PsiNamedElementExt, ObjectExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScModifierList, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScTypeAlias, ScTypeAliasDefinition, ScValue, ScVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.structureView.StructureViewUtil

private object ScalaDocumentationQuickInfoGenerator {

  def getQuickNavigateInfo(resolveResult: ScalaResolveResult): String =
    getQuickNavigateInfo(resolveResult.element, resolveResult.substitutor)

  def getQuickNavigateInfo(element: PsiElement, substitutor: ScSubstitutor): String = {
    val text = element match {
      case clazz: ScTypeDefinition                         => generateClassInfo(clazz, substitutor)
      case function: ScFunction                            => generateFunctionInfo(function, substitutor)
      case value@inNameContext(_: ScValue | _: ScVariable) => generateValueInfo(value, substitutor)
      case alias: ScTypeAlias                              => generateTypeAliasInfo(alias, substitutor)
      case parameter: ScParameter                          => generateParameterInfo(parameter, substitutor)
      case b: ScBindingPattern                             => generateBindingPatternInfo(b, substitutor)
      case _                                               => null
    }

    if (text != null) text.replace("<", "&lt;") else null
  }

  private def appendTypeParams(owner: ScTypeParametersOwner, buffer: StringBuilder): Unit =
    buffer.append(owner.typeParametersClause match {
      case Some(x) => x.getText
      case None => ""
    })

  private def generateClassInfo(clazz: ScTypeDefinition, subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    val module = ModuleUtilCore.findModuleForPsiElement(clazz)
    if (module != null) {
      buffer.append('[').append(module.getName).append("] ")
    }
    val locationString = clazz.getPresentation.getLocationString
    val length = locationString.length
    if (length > 1) buffer.append(locationString.substring(1, length - 1))
    if (buffer.nonEmpty) buffer.append("\n")
    buffer.append(getModifiersPresentableText(clazz.getModifierList))
    buffer.append(ScalaDocumentationUtils.getKeyword(clazz))
    buffer.append(clazz.name)
    appendTypeParams(clazz, buffer)
    clazz match {
      case clazz: ScClass =>
        clazz.constructor match {
          case Some(x: ScPrimaryConstructor) =>
            buffer.append(StructureViewUtil.getParametersAsString(x.parameterList, short = false, subst))
          case None =>
        }
      case _ =>
    }
    buffer.append(" extends")
    val types = clazz.superTypes
    if (types.nonEmpty) {
      for (i <- types.indices) {
        buffer.append(if (i == 1) "\n  " else " ")
        if (i != 0) buffer.append("with ")
        buffer.append(subst(types(i)).presentableText(clazz))
      }
    }
    buffer.toString()
  }

  private def generateFunctionInfo(function: ScFunction, subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(function))
    val list = function.getModifierList
    if (list != null) {
      buffer.append(getModifiersPresentableText(list))
    }
    buffer.append("def ")
    buffer.append(ScalaPsiUtil.getMethodPresentableText(function, subst))
    buffer.toString()
  }

  private def getMemberHeader(member: ScMember): String = {
    if (!member.getParent.isInstanceOf[ScTemplateBody]) return ""
    if (!member.getParent.getParent.getParent.isInstanceOf[ScTypeDefinition]) return ""
    member.containingClass.name + " " + member.containingClass.getPresentation.getLocationString + "\n"
  }

  private def generateValueInfo(field: PsiNamedElement, subst: ScSubstitutor): String = {
    val member = ScalaPsiUtil.nameContext(field) match {
      case x: ScMember => x
      case _ => return null
    }
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(member))
    buffer.append(getModifiersPresentableText(member.getModifierList))
    member match {
      case value: ScValue =>
        buffer.append("val ")
        buffer.append(field.name)
        field match {
          case typed: ScTypedDefinition =>
            val typez = subst(typed.`type`().getOrAny)
            if (typez != null) buffer.append(": " + typez.presentableText(field))
          case _ =>
        }
        value match {
          case d: ScPatternDefinition =>
            buffer.append(" = ")
            d.expr.foreach(it => buffer.append(getOneLine(it.getText)))
          case _ =>
        }
      case variable: ScVariable =>
        buffer.append("var ")
        buffer.append(field.name)
        field match {
          case typed: ScTypedDefinition =>
            val typez = subst(typed.`type`().getOrAny)
            if (typez != null) buffer.append(": " + typez.presentableText(field))
          case _ =>
        }
        variable match {
          case d: ScVariableDefinition =>
            buffer.append(" = ")
            d.expr.foreach(it => buffer.append(getOneLine(it.getText)))
          case _ =>
        }
    }
    buffer.toString()
  }

  private def getOneLine(s: String): String = {
    val trimed = s.trim
    val i = trimed.indexOf('\n')
    if (i == -1) trimed else trimed.substring(0, i) + " ..."
  }

  private def generateBindingPatternInfo(binding: ScBindingPattern, subst: ScSubstitutor): String = {
    val buffer = new StringBuilder
    buffer.append("Pattern: ")
    buffer.append(binding.name)
    val typez = subst(subst(binding.`type`().getOrAny))
    if (typez != null) buffer.append(": " + typez.presentableText(binding))

    buffer.toString()
  }

  private def generateTypeAliasInfo(alias: ScTypeAlias, subst: ScSubstitutor): String = {

    val buffer = new StringBuilder
    buffer.append(getMemberHeader(alias))
    buffer.append("type ")
    buffer.append(alias.name)
    appendTypeParams(alias, buffer)
    alias match {
      case d: ScTypeAliasDefinition =>
        buffer.append(" = ")
        val ttype = subst(d.aliasedType.getOrAny)
        buffer.append(ttype.presentableText(alias))
      case _ =>
    }
    buffer.toString()
  }

  private def generateParameterInfo(parameter: ScParameter, subst: ScSubstitutor): String =
    ScalaPsiUtil.withOriginalContextBound(parameter)(simpleParameterInfo(parameter, subst)) {
      case (typeParam, ElementText(boundText), _) =>
        val clause = typeParam.typeParametersClause.fold("")(_.getText)
        s"context bound ${typeParam.name}$clause : $boundText"
    }

  private def simpleParameterInfo(parameter: ScParameter, subst: ScSubstitutor): String = {
    val name = parameter.name
    val typeAnnot = ScalaDocumentationProvider.typeAnnotation(parameter)(subst.andThen(_.presentableText(parameter)))

    val defaultText = s"$name$typeAnnot"

    val prefix = parameter match {
      case clParameter: ScClassParameter =>
        clParameter.containingClass.toOption.map { clazz =>
          val classWithLocation = clazz.name + " " + clazz.getPresentation.getLocationString + "\n"
          val keyword = if (clParameter.isVal) "val " else if (clParameter.isVar) "var " else ""

          classWithLocation + keyword

        }.getOrElse("")
      case _ => ""
    }
    prefix + defaultText
  }

  private def getModifiersPresentableText(modList: ScModifierList): String = {
    import org.jetbrains.plugins.scala.util.EnumSet._

    modList.modifiers.toArray.map(_.text() + " ").mkString
  }
}
