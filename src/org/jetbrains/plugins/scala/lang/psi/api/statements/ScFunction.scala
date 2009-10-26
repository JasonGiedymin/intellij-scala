package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements


import collection.Seq
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi._

import psi.stubs.ScFunctionStub
import types._
import result.{TypingContext, TypeResult}

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 * Time: 9:45:38
 */

//some functions are not PsiMethods and are e.g. not visible from java
//see ScSyntheticFunction
trait ScFun extends ScTypeParametersOwner {
  def retType: ScType

  def paramTypes: Seq[ScType]

  def typeParameters: Seq[ScTypeParam]
}

trait ScFunction extends ScalaPsiElement with ScMember with ScTypeParametersOwner
        with PsiMethod with ScParameterOwner with ScDocCommentOwner with ScTypedDefinition
        with ScDeclaredElementsHolder with ScAnnotationsHolder {

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  /**
   * Returns pure `function' type as it was defined as a field with functional value
   */
  def functionType : ScFunctionType = {
    val clauseTypes : Seq[Seq[ScType]] = clauses match {
      case None => Nil
      case Some(paramClauses) => {
        val cls = paramClauses.clauses
        if (cls.length == 0) Nil
        else cls.map(cl => cl.parameters.map(_.getType(TypingContext.empty).getOrElse(Nothing)))
      }
    }
    val rt = returnType.getOrElse(Any)
    clauseTypes match {
      case Nil => ScFunctionType(rt, Nil)
      case _ => clauseTypes.foldRight(rt)((x, z) => ScFunctionType(z, x)).asInstanceOf[ScFunctionType]
    }
  }


  def paramClauses: ScParameters

  def returnTypeElement: Option[ScTypeElement] = {
    this match {
      case st: ScalaStubBasedElementImpl[_] => {
        val stub = st.getStub
        if (stub != null) {
          return stub.asInstanceOf[ScFunctionStub].getReturnTypeElement
        }
      }
      case _ =>
    }
    findChild(classOf[ScTypeElement])
  }

  def returnType: TypeResult[ScType]

  def declaredType: TypeResult[ScType] = wrap(returnTypeElement) flatMap (_.cachedType)

  def clauses: Option[ScParameters] = Some(paramClauses)

  def parameters: Seq[ScParameter]

  def paramTypes: Seq[ScType] = parameters.map{_.getType(TypingContext.empty).getOrElse(Nothing)}

  def declaredElements = Seq.singleton(this)

  def superMethods: Seq[PsiMethod]

  def superMethod: Option[PsiMethod]

  def superSignatures: Seq[FullSignature]

  def hasParamName(name: String, clausePosition: Int = -1): Boolean = getParamByName(name, clausePosition) != None
  
  def getParamByName(name: String, clausePosition: Int = -1): Option[ScParameter] = {
    clausePosition match {
      case -1 => {
        for (param <- parameters if param.name == name) return Some(param)
        return None
      }
      case i if i < 0 => return None
      case i if i >= allClauses.length => return None
      case i => {
        val clause: ScParameterClause = allClauses.apply(i)
        for (param <- clause.parameters if param.name == name) return Some(param)
        return None
      }
    }
  }
}