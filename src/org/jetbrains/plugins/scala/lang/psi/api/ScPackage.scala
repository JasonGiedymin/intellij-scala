package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState, PsiPackage}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import com.intellij.psi.search.GlobalSearchScope

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.04.2010
 */
trait ScPackage extends ScPackageLike with PsiPackage {
}