package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import stubs.ScFunctionStub
import com.intellij.lang.ASTNode

import api.statements._
import types.{ScType}
import types.result.TypeResult

/** 
* @author Alexander Podkhalyuzin
*/

class ScFunctionDeclarationImpl extends ScFunctionImpl with ScFunctionDeclaration {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScFunctionStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScFunctionDeclaration"

  def returnType: TypeResult[ScType] = wrap(typeElement) flatMap (_.cachedType)
}

