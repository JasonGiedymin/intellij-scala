package org.jetbrains.plugins.scala.codeInspection.methodSignature.quickfix

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.ProblemDescriptor
import org.jetbrains.plugins.scala.codeInspection.AbstractFix

/**
 * Pavel Fatin
 */

class RemoveTypeAnnotation(f: ScFunctionDeclaration) extends AbstractFix("Remove redundant type annotation", f) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    f.removeExplicitType()
  }
}
