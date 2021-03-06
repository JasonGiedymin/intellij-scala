package org.jetbrains.plugins.scala
package lang.refactoring.rename

import com.intellij.psi.{PsiElement, PsiReference}
import lang.resolve.ResolvableReferenceElement
import collection.JavaConverters.{asJavaCollectionConverter, iterableAsScalaIterableConverter}
import java.util
import lang.psi.api.base.ScStableCodeReferenceElement
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.toplevel.imports.ScImportStmt

object ScalaRenameUtil {
  def filterAliasedReferences(allReferences: util.Collection[PsiReference]): util.Collection[PsiReference] = {
    val filtered = allReferences.asScala.filter {
      case resolvableReferenceElement: ResolvableReferenceElement =>
        resolvableReferenceElement.bind() match {
          case Some(result) =>
            val renamed = result.isRenamed
            renamed.isEmpty
          case None => true
        }
      case _ => true
    }
    filtered.asJavaCollection
  }

  def replaceImportClassReferences(allReferences: util.Collection[PsiReference]): util.Collection[PsiReference] = {
    allReferences.asScala.map {
      case ref: ScStableCodeReferenceElement =>
        val isInImport = PsiTreeUtil.getParentOfType(ref, classOf[ScImportStmt]) != null
        if (isInImport && ref.resolve() == null) {
          val multiResolve = ref.multiResolve(false)
          if (multiResolve.length > 1 && multiResolve.forall(_.getElement.isInstanceOf[ScTypeDefinition])) {
            new PsiReference {
              def getVariants: Array[AnyRef] = ref.getVariants

              def getCanonicalText: String = ref.getCanonicalText

              def getElement: PsiElement = ref.getElement

              def isReferenceTo(element: PsiElement): Boolean = ref.isReferenceTo(element)

              def bindToElement(element: PsiElement): PsiElement = ref.bindToElement(element)

              def handleElementRename(newElementName: String): PsiElement = ref.handleElementRename(newElementName)

              def isSoft: Boolean = ref.isSoft

              def getRangeInElement: TextRange = ref.getRangeInElement

              def resolve(): PsiElement = multiResolve.apply(0).getElement
            }
          } else ref
        } else ref
      case ref: PsiReference => ref
    }.asJavaCollection
  }

}