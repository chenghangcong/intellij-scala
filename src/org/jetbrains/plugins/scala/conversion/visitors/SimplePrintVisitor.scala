package org.jetbrains.plugins.scala.conversion.visitors

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.conversion.ast.ClassConstruction.ClassType
import org.jetbrains.plugins.scala.conversion.ast.ClassConstruction.ClassType.ClassType
import org.jetbrains.plugins.scala.conversion.PrettyPrinter
import org.jetbrains.plugins.scala.conversion.ast._
import org.jetbrains.plugins.scala.conversion.ast.ModifierType.ModifierType

/**
  * Created by Kate Ustyuzhanina
  * on 11/24/15
  */
class SimplePrintVisitor extends IntermediateTreeVisitor {
  override def visit(node: IntermediateNode): Unit = {
    node match {
      case m: MainConstruction => m.children.foreach(visit)
      case t@TypeConstruction(inType) => visitType(t, inType)
      case ParametrizedConstruction(inType, parts) => visitParametrizedType(inType, parts)
      case ArrayConstruction(inType) => visitArrayType(inType)
      case TypeParameters(data) => visitTypeParameters(data)
      case TypeParameterConstruction(name, typez) => visitTypeParameterConstruction(name, typez)
      case AnnotaionConstruction(inAnnotation, attributes, name) => visitAnnotation(inAnnotation, attributes, name)
      case b@BlockConstruction(statements) => visitBlock(b, statements)
      case c@ClassConstruction(name, primaryConstructor, bodyElements, modifiers, typeParams, initalizers, classType,
      companion, extendsList) => visitClass(c, name, primaryConstructor, bodyElements,
        modifiers, typeParams, initalizers, classType, companion, extendsList)
      case a@AnonymousClass(mType, args, body, extendsList) => visitAnonymousClass(a, mType, args, body, extendsList)
      case e@Enum(name, modifiers, enumConstants: Seq[String]) => visitEnum(e, name, modifiers, enumConstants)
      case ArrayAccess(expression, idxExpression) => visitArrayAccess(expression, idxExpression)
      case c@ClassCast(operand, castType, isPrimitive) => visitCastType(c, operand, castType, isPrimitive)
      case ArrayInitializer(expresions: Seq[IntermediateNode]) => visitArrayInitalizer(expresions)
      case BinaryExpressionConstruction(firstPart, secondPart, operation: String, inExpression: Boolean) =>
        visitBinary(firstPart, secondPart, operation, inExpression)
      case ClassObjectAccess(expression) => visitClassObjAccess(expression)
      case InstanceOfConstruction(operand, mtype) => visitInstanceOf(operand, mtype)
      case QualifiedExpression(qualifier, identifier) => visitQualifiedExpression(qualifier, identifier)
      case MethodCallExpression(name, method, args, withSideEffects) => visitMethodCall(name, method, args, withSideEffects)
      case ExpressionList(data) => visitExpressionList(data)
      case ThisExpression(value) => visitWithExtraWord(value, "this")
      case SuperExpression(value) => visitWithExtraWord(value, "super")
      case LiteralExpression(literal) => printer.append(literal)
      case NameIdentifier(name) => printer.append(escapeKeyword(name))
      case ParenthesizedExpression(value) => visitParenthizedExpression(value)
      case NewExpression(mtype, arrayInitalizer, arrayDimension) =>
        visitNewExpression(mtype, arrayInitalizer, arrayDimension)
      case AnonymousClassExpression(anonymousClass) => visitAnonimousClassExpression(anonymousClass)
      case PolyadicExpression(args, operation) => visitPoliadic(args, operation)
      case PrefixExpression(operand, signType, canBeSimplified) => visitPrefixPostfix(operand, signType, canBeSimplified)
      case PostfixExpression(operand, signType, canBeSimplified) =>
        visitPrefixPostfix(operand, signType, canBeSimplified, isPostfix = true)
      case FieldConstruction(modifiers, name, ftype, isVar, initalaizer) =>
        visitVariable(modifiers, name, ftype, isVar, initalaizer)
      case LocalVariable(modifiers, name, ftype, isVar, initalaizer) =>
        visitVariable(modifiers, name, ftype, isVar, initalaizer)
      case ConstructorSimply(modifiers, typeParams, params, body) =>
        visitConstructor(modifiers, typeParams, params, body)
      case PrimaryConstruction(params, superCall, body, modifiers) =>
        visitPrimaryConstructor(params, superCall, body, modifiers)
      case MethodConstruction(modifiers, name, typeParams, params, body, retType) =>
        visitMethod(modifiers, name, typeParams, params, body, retType)
      case m@ModifiersConstruction(annotations, modifiers) => visitModifiers(m, annotations, modifiers)
      case SimpleModifier(mtype: ModifierType) => visitSimpleModifier(mtype)
      case ModifierWithExpression(mtype, value) => visitModifierWithExpr(mtype, value)
      case ParameterConstruction(modifiers, name, scCompType, isVar, isArray) =>
        visitParameters(modifiers, name, scCompType, isVar, isArray)
      case ParameterListConstruction(list) => visitParameterList(list)
      //statements
      case r@JavaCodeReferenceStatement(qualifier, parametrList, name) =>
        visitJavaCodeRef(r, qualifier, parametrList, name)
      case IfStatement(condition, thenBranch, elseBranch) => visitIfStatement(condition, thenBranch, elseBranch)
      case ReturnStatement(value) => visitWithExtraWord(Some(value), "return ")
      case ThrowStatement(value) => visitWithExtraWord(Some(value), "throw ")
      case AssertStatement(condition, description) => visitAssert(condition, description)
      case ImportStatement(importValue, onDemand) => visitImportStatement(importValue, onDemand)
      case ImportStatementList(data) => visitImportStatementList(data)
      case PackageStatement(value) => visitWithExtraWord(Some(value), "package ")
      case ForeachStatement(iterParamName, iteratedValue, body, isJavaCollection) =>
        visitForEach(iterParamName, iteratedValue, body, isJavaCollection)
      case w@WhileStatement(initialization, condition, body, update, whileType) =>
        visitWhile(w, initialization, condition, body, update, whileType)
      case TryCatchStatement(resourcesList, tryBlock, catchStatements, finallyStatements, arrow) =>
        visitTryCatch(resourcesList, tryBlock, catchStatements, finallyStatements, arrow)
      case SwitchStatemtnt(expession, body) => visitSwitchStatement(expession, body)
      case SwitchLabelStatement(caseValue, arrow) => visitSwitchLabelStatement(caseValue, arrow)
      case SynchronizedStatement(lock, body) => visitSynchronizedStatement(lock, body)
      case ExpressionListStatement(exprs) => visitExpressionListStatement(exprs)
      case NotSupported(n, msg) => visitNotSupported(n, msg)
      case EmptyConstruction() =>
    }
  }

  def visitAnnotation(inAnnotation: Boolean, attributes: Seq[(Option[IntermediateNode], Option[IntermediateNode])],
                      name: Option[IntermediateNode]): PrettyPrinter = {
    if (inAnnotation) {
      printer.append("new ")
    } else {
      printer.append("@")
    }

    if (name.isDefined) {
      name.get match {
        case deprecated: JavaCodeReferenceStatement if deprecated.name == "Deprecated" =>
          printer.append(deprecated.name.toLowerCase)
        case otherName => visit(otherName)
      }
    }

    if (attributes.nonEmpty) {
      printer.append("(")

      for ((name, value) <- attributes) {
        if (name.isDefined) {
          visit(name.get)
          printer.append(" = ")
        }

        if (value.isDefined) {
          visit(value.get)
          printer.append(", ")
        }
      }

      printer.delete(2)
      printer.append(")")
    }
    printer.space()
  }

  def visitBlock(node: BlockConstruction, statements: Seq[IntermediateNode]): Unit = {
    printBodyWithCurlyBracketes(node, () => {
      val allStatements = node.beforeStatements ++ statements
      printWithSeparator(allStatements.toSeq, "\n", "", "\n")
    })
  }

  def visitClass(c: ClassConstruction, name: IntermediateNode, primaryConstructor: Option[IntermediateNode],
                 bodyElements: Seq[IntermediateNode], modifiers: IntermediateNode,
                 typeParams: Option[Seq[IntermediateNode]], initalizers: Option[Seq[IntermediateNode]],
                 classType: ClassType, companion: IntermediateNode, extendsList: Option[Seq[IntermediateNode]]): Unit = {
    visitClassHeader()
    printBodyWithCurlyBracketes(c, () => visitClassBody())

    def visitClassHeader(): Unit = {
      if (companion.isInstanceOf[ClassConstruction]) {
        visit(companion)
        printer.newLine()
      }

      visit(modifiers)
      printer.append(classType match {
        case ClassType.CLASS => "class "
        case ClassType.OBJECT => "object "
        case ClassType.INTERFACE => "trait "
        case _ => ""
      })

      visit(name)
      if (typeParams.isDefined) printWithSeparator(typeParams.get, ", ", "[", "]", typeParams.get.nonEmpty)

      if (primaryConstructor.isDefined) {
        printer.space()
        visit(primaryConstructor.get)
      }

      if (extendsList.isDefined && extendsList.get.nonEmpty) {
        printer.append(" extends ")

        visit(extendsList.get.head)
        if (primaryConstructor.isDefined) {
          val pc = primaryConstructor.get.asInstanceOf[PrimaryConstruction]
          if (pc.superCall != null) visit(pc.superCall)
        }
        if (extendsList.get.tail.nonEmpty) printer.append(" with ")
        printWithSeparator(extendsList.get.tail, " with ")
      }
    }

    def visitClassBody(): Unit = {
      if (primaryConstructor.isDefined) {
        val pc = primaryConstructor.get.asInstanceOf[PrimaryConstruction]
        if (pc.body.isDefined) {
          printWithSeparator(pc.body.get, "\n", "", "\n")
        }
      }
      printWithSeparator(bodyElements, "\n", "", "\n", bodyElements.nonEmpty)
      if (initalizers.isDefined) printWithSeparator(initalizers.get, "\n", "\ntry ", "\n", initalizers.get.nonEmpty)
    }
  }

  def visitAnonymousClass(ac: AnonymousClass, mType: IntermediateNode, args: IntermediateNode, body: Seq[IntermediateNode],
                          extendsList: Seq[IntermediateNode]) = {
    visit(mType)
    printer.append("(")
    visit(args)
    printer.append(")")

    if (extendsList != null && extendsList.nonEmpty) {
      printer.append(" with ")
      printWithSeparator(extendsList, " with ")
    }

    printBodyWithCurlyBracketes(ac, () => printWithSeparator(body, " "))
  }

  def visitEnum(e: Enum, name: IntermediateNode, modifiers: IntermediateNode, enumConstants: Seq[String]): Unit = {
    visit(modifiers)
    printer.append("object ")
    visit(name)
    printer.append(" extends Enumeration ")

    printBodyWithCurlyBracketes(e, visitEnumBody)

    def visitEnumBody(): Unit = {
      printer.append("type ")
      visit(name)
      printer.append(" = Value\n")

      if (enumConstants.nonEmpty) {
        printer.append("val ")
        for (str <- enumConstants) {
          printer.append(str)
          printer.append(", ")
        }
        printer.delete(2)
        printer.append(" = Value\n")
      }
    }
  }

  def visitArrayAccess(expression: IntermediateNode, idxExpression: IntermediateNode): PrettyPrinter = {
    visit(expression)
    printer.append("(")
    visit(idxExpression)
    printer.append(")")
  }

  def visitCastType(c: ClassCast, operand: IntermediateNode, castType: IntermediateNode, isPrimitive: Boolean): PrettyPrinter = {
    visit(operand)
    if (c.canSimplify) {
      printer.append(".to")
      visit(castType)
    } else {
      printer.append(".asInstanceOf[")
      visit(castType)
      printer.append("]")
    }
    printer
  }

  def visitArrayInitalizer(expresions: Seq[IntermediateNode]) = {
    printWithSeparator(expresions, ", ", "Array(", ")")
  }

  def visitBinary(firstPart: IntermediateNode, secondPart: IntermediateNode, operation: String, inExpresiion: Boolean) = {
    val specialOperations = Seq("eq", "ne")
    if (inExpresiion && specialOperations.contains(operation)) printer.append("(")
    visit(firstPart)
    printer.append(" ")
    printer.append(operation)
    printer.append(" ")
    visit(secondPart)
    if (inExpresiion && specialOperations.contains(operation)) printer.append(")")
  }

  def visitClassObjAccess(expression: IntermediateNode) = {
    printer.append("classOf[")
    visit(expression)
    printer.append("]")
  }

  def visitInstanceOf(operand: IntermediateNode, mtype: IntermediateNode) = {
    visit(operand)
    printer.append(".isInstanceOf[")
    visit(mtype)
    printer.append("]")
  }

  def visitQualifiedExpression(qualifier: IntermediateNode, identifier: IntermediateNode) = {
    if (qualifier != null) {
      visit(qualifier)
      visit(identifier)
    }
    printer
  }

  def visitMethodCall(name: String, method: IntermediateNode, args: IntermediateNode, withSideEffects: Boolean) = {
    visit(method)
    if (args != null)
      visit(args)
    if (withSideEffects) printer.append("()")
  }

  def visitExpressionList(data: Seq[IntermediateNode]) = {
    printWithSeparator(data, ", ", "(", ")", data.nonEmpty)
  }

  def visitWithExtraWord(value: Option[IntermediateNode], word: String) = {
    printer.append(word)
    if (value.isDefined) visit(value.get)
  }

  def visitParenthizedExpression(value: Option[IntermediateNode]) = {
    printer.append("(")
    if (value.isDefined) visit(value.get)
    printer.append(")")
  }

  def visitNewExpression(mtype: IntermediateNode, arrayInitalizer: Seq[IntermediateNode],
                         arrayDimension: Seq[IntermediateNode]) = {
    if (arrayInitalizer.nonEmpty) {
      visit(mtype)
      printWithSeparator(arrayInitalizer, ", ", "(", ")")
    } else {
      printer.append("new ")
      visit(mtype)
      printWithSeparator(arrayDimension, ", ", "(", ")",
        arrayDimension != null && arrayDimension.nonEmpty &&
          !arrayDimension.head.isInstanceOf[ExpressionList] && arrayDimension.head != LiteralExpression("()"))
    }
  }

  def visitAnonimousClassExpression(anonClass: IntermediateNode) = {
    printer.append("new ")
    visit(anonClass)
  }

  def visitPoliadic(args: Seq[IntermediateNode], operation: String) = {
    printWithSeparator(args, " " + operation + " ")
  }

  def visitPrefixPostfix(operand: IntermediateNode, signType: String,
                         canBeSimplified: Boolean, isPostfix: Boolean = false): Unit = {
    signType match {
      case "++" =>
        if (!canBeSimplified) {
          printer.append("{")
          visit(operand)
          printer.append(" += 1; ")
          visit(operand)
          if (isPostfix) printer.append(" - 1")
          printer.append("}")
        } else {
          visit(operand)
          printer.append(" += 1")
        }
      case "--" =>
        if (!canBeSimplified) {
          printer.append("{")
          visit(operand)
          printer.append(" -= 1; ")
          visit(operand)
          if (isPostfix) printer.append(" + 1")
          printer.append("}")
        } else {
          visit(operand)
          printer.append(" -= 1")
        }
      case _ if !isPostfix =>
        printer.append(signType)
        visit(operand)
    }
  }

  def visitVariable(modifiers: IntermediateNode, name: IntermediateNode,
                    ftype: IntermediateNode, isVar: Boolean,
                    initalaizer: Option[IntermediateNode]) = {
    visit(modifiers)

    if (isVar) {
      printer.append("var")
    } else {
      printer.append("val")
    }
    printer.space()
    visit(name)
    printer.append(": ")
    visit(ftype)
    printer.append(" = ")
    if (initalaizer.isDefined) {
      visit(initalaizer.get)
    } else {
      printer.append(ftype match {
        case tc: TypeConstruction => tc.getDefaultTypeValue
        case _ => "null"
      })
    }
  }

  def visitConstructor(modifiers: IntermediateNode, typeParams: Seq[IntermediateNode],
                       params: Seq[IntermediateNode], body: Option[IntermediateNode]) = {
    printer.append("def ")
    printer.append("this")
    if (typeParams.nonEmpty) {
      printWithSeparator(typeParams, ", ", "[", "]")
    }

    printWithSeparator(params, ", ", "(", ")", params.nonEmpty)
    if (body.isDefined) visit(body.get)
  }

  def visitMethod(modifiers: IntermediateNode, name: IntermediateNode, typeParams: Seq[IntermediateNode],
                  params: Seq[IntermediateNode], body: Option[IntermediateNode], retType: IntermediateNode) = {
    visit(modifiers)
    printer.append("def ")
    visit(name)

    if (typeParams.nonEmpty) {
      printWithSeparator(typeParams, ", ", "[", "]")
    }


    printWithSeparator(params, ", ", "(", ")", params.nonEmpty || (params.isEmpty && retType == null))

    if (retType != null) {
      printer.append(": ")
      visit(retType)
    }

    if (body.isDefined) {
      if (retType != null) printer.append(" = ")
      visit(body.get)
    }
  }

  def visitPrimaryConstructor(params: Seq[IntermediateNode], superCall: IntermediateNode, body: Option[Seq[IntermediateNode]],
                              modifiers: IntermediateNode) = {
    visit(modifiers)
    printer.space()
    printer.append("(")
    printWithSeparator(params, ", ")
    printer.space()
    printer.append(")")
  }

  def visitModifiers(modifiersConstruction: ModifiersConstruction, annotations: Seq[IntermediateNode], modifiers: Seq[IntermediateNode]) = {
    for (a <- annotations) {
      visit(a)
      printer.space()
    }

    //to prevent situation where access modifiers print earlier then throw
    val sortModifiers = modifiers.collect { case m: Modifier if !modifiersConstruction.accessModifiers.contains(m.modificator) => m } ++
      modifiers.collect { case m: Modifier if modifiersConstruction.accessModifiers.contains(m.modificator) => m }

    for (m <- sortModifiers) {
      if (!modifiersConstruction.withoutList.contains(m.asInstanceOf[Modifier].modificator)) {
        visit(m)
        printer.space()
      }
    }
  }

  def visitSimpleModifier(mtype: ModifierType) = {
    printer.append(mtype match {
      case ModifierType.ABSTRACT => "abstract"
      case ModifierType.PUBLIC => "public"
      case ModifierType.PROTECTED => "protected"
      case ModifierType.PRIVATE => "private"
      case ModifierType.OVERRIDE => "override"
      case ModifierType.FINAL => "final"
      case _ => ""
    })
  }

  def visitModifierWithExpr(mtype: ModifierType, value: IntermediateNode) = {
    mtype match {
      case ModifierType.THROW =>
        printer.append("@throws[")
        visit(value)
        printer.append("]\n")
      case ModifierType.SerialVersionUID =>
        printer.append("@SerialVersionUID(")
        visit(value)
        printer.append(")\n")
      case ModifierType.PRIVATE =>
        printer.append("private[")
        visit(value)
        printer.append("] ")
      case _ =>
    }
  }

  def visitParameters(modifiers: IntermediateNode, name: IntermediateNode,
                      scCompType: IntermediateNode, isVar:Option[Boolean], isArray: Boolean) = {
    visit(modifiers)
    if (isVar.isDefined) {
      if (isVar.get) printer.append("var ")
      else printer.append("val ")
    }
    visit(name)
    printer.append(": ")
    visit(scCompType)
    if (isArray) {
      printer.append("*")
    }
  }

  def visitParameterList(list: Seq[IntermediateNode]) = {
    printWithSeparator(list, ", ", "(", ")", list.nonEmpty)
  }


  def visitIfStatement(condition: Option[IntermediateNode], thenBranch: Option[IntermediateNode],
                       elseBranch: Option[IntermediateNode]) = {

    printer.append("if")
    printer.space()

    printer.append("(")
    if (condition.isDefined) visit(condition.get)
    printer.append(")")
    printer.space()

    if (thenBranch.isDefined) visit(thenBranch.get)
    if (elseBranch.isDefined) {
      printer.newLine()
      printer.append("else")
      printer.space()
      visit(elseBranch.get)
    }
  }

  def visitAssert(condition: Option[IntermediateNode], description: Option[IntermediateNode]) = {
    printer.append("assert (")
    if (condition.isDefined) visit(condition.get)
    if (description.isDefined) {
      printer.append(", ")
      visit(description.get)
    }
    printer.append(")")
  }

  def visitImportStatement(importValue: IntermediateNode, onDemand: Boolean) = {
    printer.append("import ")
    visit(importValue)
    if (onDemand) {
      printer.append("._")
    }
  }

  def visitImportStatementList(imports: Seq[IntermediateNode]) = {
    for (iimport <- imports) {
      visit(iimport)
      printer.newLine()
    }
  }

  def visitWhile(w: WhileStatement, initialization: Option[IntermediateNode], condition: Option[IntermediateNode],
                 body: Option[IntermediateNode], update: Option[IntermediateNode], whileType: Int): Unit = {
    def printDoWhile(): PrettyPrinter = {
      printer.append("do {\n")
      printBodyWithCurlyBracketes(w, () => if (body.isDefined) visit(body.get))
      printer.append("\n}")
      if (update.isDefined) {
        printer.newLine()
        visit(update.get)
      }
      printer.append("while")
      printer.space()
      printer.append("(")
      if (condition.isDefined) visit(condition.get)
      printer.append(")")
    }

    def printWhile(): Unit = {
      printer.append("while")
      printer.space()
      printer.append("(")
      if (condition.isDefined) visit(condition.get)
      printer.append(")")
      printer.space()
      printBodyWithCurlyBracketes(w, () => {
        if (body.isDefined) visit(body.get)
        if (update.isDefined) {
          printer.newLine()
          visit(update.get)
        }
      })
    }

    if (initialization.isDefined) {
      visit(initialization.get)
      printer.newLine()
    }

    if (whileType == WhileStatement.PRE_TEST_LOOP) {
      printWhile()
    } else if (whileType == WhileStatement.POST_TEST_LOOP) {
      printDoWhile()
    }
  }

  def visitTryCatch(resourcesList: Seq[(String, IntermediateNode)], tryBlock: Option[IntermediateNode],
                    catchStatements: Seq[(IntermediateNode, IntermediateNode)],
                    finallyStatements: Option[Seq[IntermediateNode]], arrow: String) = {
    if (resourcesList != null && resourcesList.nonEmpty) {
      printer.append("try {\n")
      printWithSeparator(resourcesList.map(_._2), "\n", "", "\n")
    }

    printer.append("try ")
    if (tryBlock.isDefined) visit(tryBlock.get)

    if (catchStatements.nonEmpty) {
      printer.append("\ncatch {\n")
      for ((parameter, block) <- catchStatements) {
        printer.append("case ")
        visit(parameter)
        printer.append(s" $arrow ")
        visit(block)
      }
      printer.append("}")
    }
    if (finallyStatements.isDefined) {
      if (resourcesList == null) {
        printer.append(" finally ")
        printWithSeparator(finallyStatements.get, "\n")
      } else {
        printer.append(" finally {\n")
        printWithSeparator(finallyStatements.get, "\n", "", "\n")
        resourcesList.foreach {
          case (name: String, variable: IntermediateNode) =>
            val cname = escapeKeyword(name)
            printer.append(s"if ($cname != null) $cname.close()\n")
        }

        printer.append("}")
      }
    } else if (resourcesList.nonEmpty) {
      printer.append(" finally {\n")
      resourcesList.foreach {
        case (name: String, variable: IntermediateNode) =>
          val cname = escapeKeyword(name)
          printer.append(s"if ($cname != null) $cname.close()\n")
      }
      printer.append("}")
    }
    if (resourcesList.nonEmpty) {
      printer.append("\n}")
    }
  }

  def visitSwitchStatement(expession: Option[IntermediateNode], body: Option[IntermediateNode]) = {
    if (expession.isDefined) visit(expession.get)
    printer.append(" match ")
    if (body.isDefined) visit(body.get)
  }

  def visitSwitchLabelStatement(caseValue: Option[IntermediateNode], arrow: String) = {
    printer.append("case ")
    if (caseValue.isDefined) visit(caseValue.get)
    printer.append(s" $arrow ")
  }

  def visitNotSupported(iNode: Option[IntermediateNode], msg: String) = {
    printer.append(msg)
    if (iNode.isDefined) {
      printer.newLine()
      visit(iNode.get)
    }
  }

  def visitSynchronizedStatement(lock: Option[IntermediateNode], body: Option[IntermediateNode]) = {
    if (lock.isDefined) visit(lock.get)
    printer.append(" synchronized ")
    if (body.isDefined) visit(body.get)
  }

  def visitExpressionListStatement(exprs: Seq[IntermediateNode]) = {
    printWithSeparator(exprs, "\n")
  }

  def visitForEach(iterParamName: IntermediateNode, iteratedValue: Option[IntermediateNode],
                   body: Option[IntermediateNode], isJavaCollection: Boolean) = {
    if (isJavaCollection) {
      printer.append("import scala.collection.JavaConversions._\n")
    }

    printer.append("for (")
    visit(iterParamName)
    printer.append(" <- ")
    if (iteratedValue.isDefined) visit(iteratedValue.get)
    printer.append(") ")
    if (body.isDefined) visit(body.get)
  }

  def visitJavaCodeRef(statement: JavaCodeReferenceStatement, qualifier: Option[IntermediateNode], parametrList: Option[IntermediateNode], name: String) = {
    if (qualifier.isDefined) {
      visit(qualifier.get)
      printer.append(".")
    }
    val begin = printer.length
    name match {
      case "this" => printer.append(name)
      case "super" => printer.append(name)
      case _ => printer.append(escapeKeyword(name))
    }
    val range = new TextRange(begin, printer.length)
    rangedElementsMap.put(statement, range)
    if (parametrList.isDefined) visit(parametrList.get)
  }

  def visitType(t: TypeConstruction, inType: String) = {
    val begin = printer.length
    printer.append(inType)
    val range = new TextRange(begin, printer.length)
    rangedElementsMap.put(t, range)
  }

  def visitArrayType(iNode: IntermediateNode) = {
    printer.append("Array[")
    visit(iNode)
    printer.append("]")
  }

  def visitParametrizedType(iNode: IntermediateNode, parts: Seq[IntermediateNode]) = {
    visit(iNode)
    printWithSeparator(parts, ", ", "[", "]", parts.nonEmpty)
  }

  def printWithSeparator(seq: Seq[IntermediateNode], separator: String): Unit = {
    if (seq != null && seq.nonEmpty) {
      val it = seq.iterator
      while (it.hasNext) {
        visit(it.next())
        if (it.hasNext) printer.append(separator)
      }
    }
  }

  def printWithSeparator(seq: Seq[IntermediateNode], separator: String, before: String, after: String, needAppend: Boolean): Unit = {
    if (needAppend) printer.append(before)
    printWithSeparator(seq, separator)
    if (needAppend) printer.append(after)
  }

  def printWithSeparator(seq: Seq[IntermediateNode], separator: String, before: String, after: String): Unit = {
    printWithSeparator(seq, separator, before, after, needAppend = true)
  }

  def visitTypeParameters(data: Seq[IntermediateNode]) = {
    printWithSeparator(data, ", ", "[", "]", data.nonEmpty)
  }

  def visitTypeParameterConstruction(name: IntermediateNode, typez: Seq[IntermediateNode]) = {
    visit(name)
    if (typez.nonEmpty) {
      printer.append(" <: ")
      printWithSeparator(typez, " with ")
    }
  }

  def printBodyWithCurlyBracketes(node: IntermediateNode, printBodyFunction: () => Unit): Unit = {
    printer.append(" { ")
    printBodyFunction()
    printer.append(" } ")
  }

  override val printer: PrettyPrinter = new PrettyPrinter
}
