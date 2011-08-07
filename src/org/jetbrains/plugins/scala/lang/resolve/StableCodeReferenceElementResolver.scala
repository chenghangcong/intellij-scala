package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi.impl.source.resolve.ResolveCache
import processor._
import psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern}
import psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import psi.api.base.ScStableCodeReferenceElement
import psi.types.Compatibility.Expression
import psi.api.base.types.ScParameterizedTypeElement

class StableCodeReferenceElementResolver(reference: ResolvableStableCodeReferenceElement, shapeResolve: Boolean,
                                          allConstructorResults: Boolean, noConstructorResolve: Boolean)
        extends ResolveCache.PolyVariantResolver[ScStableCodeReferenceElement] {
  def resolve(ref: ScStableCodeReferenceElement, incomplete: Boolean) = {
    val kinds = ref.getKinds(false)

    val proc = if (ref.isConstructorReference && !noConstructorResolve) {
      val constr = ref.getConstructor.get
      val typeArgs = constr.typeElement match {
        case pte: ScParameterizedTypeElement => pte.typeArgList.typeArgs
        case _ => Seq()
      }
      val effectiveArgs = constr.arguments.toList.map(_.exprs.map(new Expression(_))) match {
        case List() => List(List())
        case x => x
      }
      new ConstructorResolveProcessor(ref, ref.refName, effectiveArgs, typeArgs, kinds, shapeResolve, allConstructorResults)
    } else ref.getContext match {
      //last ref may import many elements with the same name
      case e: ScImportExpr if (e.selectorSet == None && !e.singleWildcard) =>
        new CollectAllProcessor(kinds, ref, reference.refName)
      case e: ScImportExpr if e.singleWildcard => new ResolveProcessor(kinds, ref, reference.refName)
      case _: ScImportSelector => new CollectAllProcessor(kinds, ref, reference.refName)
      case constr: ScConstructorPattern =>
        new ExtractorResolveProcessor(ref, reference.refName, kinds, constr.expectedType)
      case infix: ScInfixPattern => new ExtractorResolveProcessor(ref, reference.refName, kinds, infix.expectedType)
      case _ => new ResolveProcessor(kinds, ref, reference.refName)
    }
    reference.doResolve(ref, proc)
  }
}
