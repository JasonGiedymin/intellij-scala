package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import com.intellij.openapi.project.Project

import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiFile
import com.intellij.openapi.editor.Editor
import java.lang.String
import lang.psi.api.ScalaFile
import lang.psi.api.statements.{ScValue, ScPatternDefinition, ScVariableDefinition}

class VarToValQuickFix(varDef: ScVariableDefinition) extends LocalQuickFix {
  def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    val parent = varDef.getContext
    varDef.replace(ScalaPsiElementFactory.createValFromVarDefinition(varDef, varDef.getManager))
  }

  def getName: String = "Convert 'var' to 'val'"

  def getFamilyName: String = getName
}

class ValToVarQuickFix(valDef: ScValue) extends IntentionAction {
  def startInWriteAction: Boolean = true

  def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    val parent = valDef.getContext
    valDef.replace(ScalaPsiElementFactory.createVarFromValDeclaration(valDef, valDef.getManager))
  }

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = file.isInstanceOf[ScalaFile]

  def getFamilyName: String = getText

  def getText: String = "Convert 'val' to 'var'"
}