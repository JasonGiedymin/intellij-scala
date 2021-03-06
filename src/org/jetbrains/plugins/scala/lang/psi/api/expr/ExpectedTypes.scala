package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import base.patterns.ScCaseClause
import statements._
import params.ScParameter
import base.ScConstructor
import collection.mutable.ArrayBuffer
import types._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi._
import nonvalue.{Parameter, ScTypePolymorphicType, ScMethodType}
import toplevel.ScTypedDefinition
import result.{TypeResult, Success, TypingContext}
import base.types.{ScSequenceArg, ScTypeElement}
import lang.resolve.ScalaResolveResult
import psi.impl.ScalaPsiManager
import toplevel.typedef.ScObject

/**
 * @author ilyas
 *
 * Utility object to calculate expected type of any expression
 */

private[expr] object ExpectedTypes {
  /**
   * Do not use this method inside of resolve or type inference.
   * Using this leads to SOE.
   */
  def smartExpectedType(expr: ScExpression, fromUnderscore: Boolean = true): Option[ScType] =
    smartExpectedTypeEx(expr, fromUnderscore).map(_._1)

  def smartExpectedTypeEx(expr: ScExpression, fromUnderscore: Boolean = true): Option[(ScType, Option[ScTypeElement])] = {
    val types = expectedExprTypes(expr, withResolvedFunction = true, fromUnderscore = fromUnderscore)
    types.length match {
      case 1 => Some(types(0))
      case _ => None
    }
  }
  
  def expectedExprType(expr: ScExpression, fromUnderscore: Boolean = true): Option[(ScType, Option[ScTypeElement])] = {
    val types = expr.expectedTypesEx(fromUnderscore)
    types.length match {
      case 1 => Some(types(0))
      case _ => None
    }
  }

  implicit def typeToPair(tpe: ScType): (ScType, Option[ScTypeElement]) = (tpe, None) // TODO jzaugg remove

  /**
   * @return (expectedType, expectedTypeElement)
   */
  def expectedExprTypes(expr: ScExpression, withResolvedFunction: Boolean = false,
                        fromUnderscore: Boolean = true): Array[(ScType, Option[ScTypeElement])] = {
    val result: Array[(ScType, Option[ScTypeElement])] = expr.getContext match {
      case p: ScParenthesisedExpr => p.expectedTypesEx(fromUnderscore)
      //see SLS[6.11]
      case b: ScBlockExpr => b.lastExpr match {
        case Some(e) if b.needCheckExpectedType && e == expr.getSameElementInContext => b.expectedTypesEx(fromUnderscore = true)
        case _ => Array.empty
      }
      //see SLS[6.16]
      case cond: ScIfStmt if cond.condition.getOrElse(null: ScExpression) == expr.getSameElementInContext => Array((types.Boolean, None))
      case cond: ScIfStmt if cond.elseBranch != None => cond.expectedTypesEx(fromUnderscore = true)
      //see SLA[6.22]
      case tb: ScTryBlock => tb.lastExpr match {
        case Some(e) if e == expr => tb.getContext.asInstanceOf[ScTryStmt].expectedTypesEx(fromUnderscore = true)
        case _ => Array.empty
      }
      case wh: ScWhileStmt if wh.condition.getOrElse(null: ScExpression) == expr.getSameElementInContext => Array((types.Boolean, None))
      case wh: ScWhileStmt => Array((types.Unit, None))
      case d: ScDoStmt if d.condition.getOrElse(null: ScExpression) == expr.getSameElementInContext => Array((types.Boolean, None))
      case d: ScDoStmt => Array((types.Unit, None))
      case fb: ScFinallyBlock => Array((types.Unit, None))
      case cb: ScCatchBlock => Array.empty
      case te: ScThrowStmt =>
        // Not in the SLS, but in the implementation.
        val throwableClass = ScalaPsiManager.instance(te.getProject).getCachedClass(te.getResolveScope, "java.lang.Throwable")
        val throwableType = if (throwableClass != null) new ScDesignatorType(throwableClass) else Any
        Array((throwableType, None))
      //see SLS[8.4]
      case c: ScCaseClause => c.getContext.getContext match {
        case m: ScMatchStmt => m.expectedTypesEx(fromUnderscore = true)
        case b: ScBlockExpr if b.isAnonymousFunction && b.getContext.isInstanceOf[ScCatchBlock] =>
          b.getContext.getContext.asInstanceOf[ScTryStmt].expectedTypesEx(fromUnderscore = true)
        case b: ScBlockExpr if b.isAnonymousFunction => {
          b.expectedTypesEx(fromUnderscore = true).flatMap(tp => ScType.extractFunctionType(tp._1) match {
            case Some(ScFunctionType(retType, _)) => Array[(ScType, Option[ScTypeElement])]((retType, None))
            case _ => ScType.extractPartialFunctionType(tp._1) match {
              case Some((des, param, ret)) => Array[(ScType, Option[ScTypeElement])]((ret, None))
              case None => Array[(ScType, Option[ScTypeElement])]()
            }
          })
        }
        case _ => Array.empty
      }
      //see SLS[6.23]
      case f: ScFunctionExpr => f.expectedTypesEx(fromUnderscore = true).flatMap(tp => ScType.extractFunctionType(tp._1) match {
        case Some(ScFunctionType(retType, _)) => Array[(ScType, Option[ScTypeElement])]((retType, None))
        case _ => Array[(ScType, Option[ScTypeElement])]()
      })
      case t: ScTypedStmt if t.getLastChild.isInstanceOf[ScSequenceArg] => {
        t.expectedTypesEx(fromUnderscore = true)
      }
      //SLS[6.13]
      case t: ScTypedStmt => {
        t.typeElement match {
          case Some(te) => Array((te.getType(TypingContext.empty).getOrAny, Some(te)))
          case _ => Array.empty
        }
      }
      //SLS[6.15]
      case a: ScAssignStmt if a.getRExpression.getOrElse(null: ScExpression) == expr.getSameElementInContext => {
        a.getLExpression match {
          case ref: ScReferenceExpression if !a.getContext.isInstanceOf[ScArgumentExprList] ||
                  ref.qualifier.isDefined ||
                  expr.isInstanceOf[ScUnderscoreSection] /* See SCL-3512, SCL-3525, SCL-4809 */ => {
            ref.bind() match {
              case Some(ScalaResolveResult(named: PsiNamedElement, subst: ScSubstitutor)) => {
                ScalaPsiUtil.nameContext(named) match {
                  case v: ScValue =>
                    Array((subst.subst(named.asInstanceOf[ScTypedDefinition].
                      getType(TypingContext.empty).getOrAny), v.typeElement))
                  case v: ScVariable =>
                    Array((subst.subst(named.asInstanceOf[ScTypedDefinition].
                      getType(TypingContext.empty).getOrAny), v.typeElement))
                  case f: ScFunction if f.paramClauses.clauses.length == 0 =>
                    a.mirrorMethodCall match {
                      case Some(call) =>
                        call.args.exprs(0).expectedTypesEx(fromUnderscore = fromUnderscore)
                      case None => Array.empty
                    }
                  case p: ScParameter => {
                    //for named parameters
                    Array((subst.subst(p.getType(TypingContext.empty).getOrAny), p.typeElement))
                  }
                  case _ => Array.empty
                }
              }
              case _ => Array.empty
            }
          }
          case ref: ScReferenceExpression => expectedExprTypes(a)
          case call: ScMethodCall => Array.empty//todo: as argumets call expected type
          case _ => Array.empty
        }
      }
      //method application
      case tuple: ScTuple if tuple.isCall => {
        val res = new ArrayBuffer[ScType]
        val exprs: Seq[ScExpression] = tuple.exprs
        val actExpr = expr.getDeepSameElementInContext
        val i = if (actExpr == null) 0 else exprs.indexWhere(_ == actExpr)
        val callExpression = tuple.getContext.asInstanceOf[ScInfixExpr].operation
        if (callExpression != null) {
          val tps = callExpression match {
            case ref: ScReferenceExpression =>
              if (!withResolvedFunction) ref.shapeMultiType
              else ref.multiType
            case _ => Array(callExpression.getNonValueType(TypingContext.empty))
          }
          tps.foreach(processArgsExpected(res, expr, i, _, exprs))
        }
        res.map(typeToPair).toArray
      }
      case tuple: ScTuple => {
        val buffer = new ArrayBuffer[ScType]
        val exprs = tuple.exprs
        val actExpr = expr.getDeepSameElementInContext
        val index = exprs.indexOf(actExpr)
        for (tp: ScType <- tuple.expectedTypes(fromUnderscore = true)) {
          ScType.extractTupleType(tp) match {
            case Some(ScTupleType(comps)) if comps.length == tuple.exprs.length => {
              buffer += comps(index)
            }
            case _ =>
          }
        }
        buffer.map(typeToPair).toArray
      }
      case infix: ScInfixExpr if ((infix.isLeftAssoc && infix.lOp == expr.getSameElementInContext) ||
              (!infix.isLeftAssoc && infix.rOp == expr.getSameElementInContext)) && !expr.isInstanceOf[ScTuple] => {
        val res = new ArrayBuffer[ScType]
        val zExpr: ScExpression = expr match {
          case p: ScParenthesisedExpr => p.expr.getOrElse(return Array.empty)
          case _ => expr
        }
        val op = infix.operation
        var tps = if (!withResolvedFunction) op.shapeMultiType else op.multiType
        tps = tps.map(infix.updateAccordingToExpectedType(_))
        tps.foreach(processArgsExpected(res, zExpr, 0, _, Seq(zExpr), Some(infix)))
        res.map(typeToPair).toArray
      }
      //SLS[4.1]
      case v @ ScPatternDefinition.expr(expr) if expr == expr.getSameElementInContext => {
        v.typeElement match {
          case Some(te) => Array((v.getType(TypingContext.empty).getOrAny, Some(te)))
          case _ => Array.empty
        }
      }
      case v @ ScVariableDefinition.expr(expr) if expr == expr.getSameElementInContext => {
        v.typeElement match {
          case Some(te) => Array((v.getType(TypingContext.empty).getOrAny, Some(te)))
          case _ => Array.empty
        }
      }
      //SLS[4.6]
      case v: ScFunctionDefinition if (v.body match {
        case None => false
        case Some(b) => b == expr.getSameElementInContext
      }) => {
        v.returnTypeElement match {
          case Some(te) => v.returnType.toOption.map(x => (x, Some(te))).toArray
          case _ => v.getInheritedReturnType.map(typeToPair).toArray
        }
      }
      //default parameters
      case param: ScParameter => {
        param.typeElement match {
          case Some(_) => Array((param.getType(TypingContext.empty).getOrAny, param.typeElement))
          case _ => Array.empty
        }
      }
      case ret: ScReturnStmt => {
        val fun: ScFunction = PsiTreeUtil.getContextOfType(ret, true, classOf[ScFunction])
        if (fun == null) return Array.empty
        fun.returnTypeElement match {
          case Some(rte: ScTypeElement) => {
            fun.returnType match {
              case Success(rt: ScType, _) => Array((rt, Some(rte)))
              case _ => Array.empty
            }
          }
          case None => Array.empty
        }
      }
      case args: ScArgumentExprList => {
        val res = new ArrayBuffer[ScType]
        val exprs: Seq[ScExpression] = args.exprs
        val actExpr = expr.getDeepSameElementInContext
        val i = if (actExpr == null) 0 else exprs.indexWhere(_ == actExpr)
        val callExpression = args.callExpression
        if (callExpression != null) {
          var tps: Array[TypeResult[ScType]] = callExpression match {
            case ref: ScReferenceExpression =>
              if (!withResolvedFunction) ref.shapeMultiType
              else ref.multiType
            case gen: ScGenericCall =>
              if (!withResolvedFunction) gen.shapeMultiType
              else gen.multiType
            case _ => Array(callExpression.getNonValueType(TypingContext.empty))
          }
          val callOption = args.getParent match {
            case call: MethodInvocation => Some(call)
            case _ => None
          }
          callOption.foreach(call => tps = tps.map(call.updateAccordingToExpectedType(_)))
          tps.foreach(processArgsExpected(res, expr, i, _, exprs, callOption))
        } else {
          //it's constructor
          args.getContext match {
            case constr: ScConstructor => {
              val j = constr.arguments.indexOf(args)
              var tps =
                if (!withResolvedFunction) constr.shapeMultiType(j)
                else constr.multiType(j)
              tps.foreach(processArgsExpected(res, expr, i, _, exprs))
            }
            case s: ScSelfInvocation => {
              val j = s.arguments.indexOf(args)
              if (!withResolvedFunction) s.shapeMultiType(j).foreach(processArgsExpected(res, expr, i, _, exprs))
              else s.multiType(j).foreach(processArgsExpected(res, expr, i, _, exprs))
            }
            case _ =>
          }
        }
        res.map(typeToPair).toArray
      }
      case b: ScBlock if b.getContext.isInstanceOf[ScTryBlock]
              || b.getContext.getContext.getContext.isInstanceOf[ScCatchBlock]
              || b.getContext.isInstanceOf[ScCaseClause] 
              || b.getContext.isInstanceOf[ScFunctionExpr] => b.lastExpr match {
        case Some(e) if expr.getSameElementInContext == e => b.expectedTypesEx(fromUnderscore = true)
        case _ => Array.empty
      }
      case _ => Array.empty
    }

    if (fromUnderscore && ScUnderScoreSectionUtil.underscores(expr).length != 0) {
      val res = new ArrayBuffer[(ScType, Option[ScTypeElement])]
      for (tp <- result) {
        ScType.extractFunctionType(tp._1) match {
          case Some(ScFunctionType(rt: ScType, _)) => res += rt
          case None =>
        }
      }
      res.toArray
    } else result
  }

  private def processArgsExpected(res: ArrayBuffer[ScType], expr: ScExpression, i: Int, tp: TypeResult[ScType],
                                  exprs: Seq[ScExpression], call: Option[MethodInvocation] = None,
                                  forApply: Boolean = false) {
    def applyForParams(params: Seq[Parameter]) {
      val p: ScType =
        if (i >= params.length && params.length > 0 && params(params.length - 1).isRepeated)
          params(params.length - 1).paramType
        else if (i >= params.length) Nothing
        else params(i).paramType
      if (expr.isInstanceOf[ScAssignStmt]) {
        val assign = expr.asInstanceOf[ScAssignStmt]
        val lE = assign.getLExpression
        lE match {
          case ref: ScReferenceExpression if ref.qualifier == None => {
            val name = ref.refName
            params.find(_.name == name) match {
              case Some(param) => res += param.paramType
              case _ => res += p
            }
          }
          case _ => res += p
        }
      } else if (expr.isInstanceOf[ScTypedStmt] && expr.asInstanceOf[ScTypedStmt].isSequenceArg && params.length > 0) {
        val seqClass: Array[PsiClass] = ScalaPsiManager.instance(expr.getProject).
          getCachedClasses(expr.getResolveScope, "scala.collection.Seq").filter(!_.isInstanceOf[ScObject])
        if (seqClass.length != 0) {
          val tp = ScParameterizedType(ScType.designator(seqClass(0)), Seq(params(params.length - 1).paramType))
          res += tp
        }
      } else res += p
    }
    tp match {
      case Success(ScMethodType(_, params, _), _) => {
        if (params.length == 1 && !params.apply(0).isRepeated && exprs.length > 1) {
          ScType.extractTupleType(params.apply(0).paramType) match {
            case Some(ScTupleType(args)) => applyForParams(args.zipWithIndex.map {
              case (tpe, index) => new Parameter("", tpe, false, false, false, index)
            })
            case None =>
          }
        } else applyForParams(params)
      }
      case Success(t@ScTypePolymorphicType(ScMethodType(_, params, _), typeParams), _) => {
        val subst = t.abstractTypeSubstitutor
        val newParams = params.map(p => p.copy(paramType = subst.subst(p.paramType)))
        if (newParams.length == 1 && !newParams.apply(0).isRepeated && exprs.length > 1) {
          ScType.extractTupleType(newParams.apply(0).paramType) match {
            case Some(ScTupleType(args)) => applyForParams(args.zipWithIndex.map {
              case (tpe, index) => new Parameter("", tpe, false, false, false, index)
            })
            case None =>
          }
        } else applyForParams(newParams)
      }
      case Success(t@ScTypePolymorphicType(anotherType, typeParams), _) if !forApply => {
        val cand = call.getOrElse(expr).applyShapeResolveForExpectedType(anotherType, exprs, call, tp)
        if (cand.length == 1) {
          cand(0) match {
            case ScalaResolveResult(fun: ScFunction, s) => {
              var polyType: TypeResult[ScType] = Success(s.subst(fun.polymorphicType) match {
                case ScTypePolymorphicType(internal, params) =>
                  ScTypePolymorphicType(internal, params ++ typeParams)
                case tp => ScTypePolymorphicType(tp, typeParams)
              }, Some(expr))
              call.foreach(call => polyType = call.updateAccordingToExpectedType(polyType))
              processArgsExpected(res, expr, i, polyType, exprs, forApply = true)
            }
            case _ =>
          }
        }
      }
      case Success(anotherType, _) if !forApply => {
        val cand = call.getOrElse(expr).applyShapeResolveForExpectedType(anotherType, exprs, call, tp)
        if (cand.length == 1) {
          cand(0) match {
            case ScalaResolveResult(fun: ScFunction, subst) =>
              var polyType: TypeResult[ScType] = Success(subst.subst(fun.polymorphicType), Some(expr))
              call.foreach(call => polyType = call.updateAccordingToExpectedType(polyType))
              processArgsExpected(res, expr, i, polyType, exprs, forApply = true)
            case _ =>
          }
        }
      }
      case _ =>
    }
  }
}