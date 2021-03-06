package org.jetbrains.plugins.scala.codeInsight.intentions.controlflow

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.codeInsight.intention.controlflow.MergeIfToAndIntention

/**
 * @author Ksenia.Sautina
 * @since 6/6/12
 */

class MergeIfToAndIntentionTest extends ScalaIntentionTestBase {
  val familyName = MergeIfToAndIntention.familyName

  def testMergeIfToAnd() {
    val text = """
                 |class MergeIfToAnd {
                 |  def mthd() {
                 |    val a: Int = 0
                 |    i<caret>f (a == 9) {
                 |      if (a == 7) {
                 |        System.out.println("if")
                 |      }
                 |    }
                 |  }
                 |}
               """.stripMargin.replace("\r", "").trim
    val resultText = """
                       |class MergeIfToAnd {
                       |  def mthd() {
                       |    val a: Int = 0
                       |    i<caret>f (a == 9 && a == 7) {
                       |      System.out.println("if")
                       |    }
                       |  }
                       |}
                     """.stripMargin.replace("\r", "").trim

    doTest(text, resultText)
  }

  def testMergeIfToAnd2() {
    val text = """
                 |class MergeIfToAnd {
                 |  def mthd() {
                 |    val a: Int = 0
                 |    i<caret>f (a == 9)
                 |      if (a == 7)
                 |        System.out.println("if")
                 |  }
                 |}
               """.stripMargin.replace("\r", "").trim
    val resultText = """
                       |class MergeIfToAnd {
                       |  def mthd() {
                       |    val a: Int = 0
                       |    i<caret>f (a == 9 && a == 7) System.out.println("if")
                       |  }
                       |}
                     """.stripMargin.replace("\r", "").trim

    doTest(text, resultText)
  }

  def testMergeIfToAnd3() {
    val text = """
                 |class MergeIfToAnd {
                 |  def mthd() {
                 |    val a: Int = 0
                 |    i<caret>f (a == 9) {
                 |      if (a == 7)
                 |        System.out.println("if")
                 |    }
                 |  }
                 |}
               """.stripMargin.replace("\r", "").trim
    val resultText = """
                       |class MergeIfToAnd {
                       |  def mthd() {
                       |    val a: Int = 0
                       |    i<caret>f (a == 9 && a == 7) System.out.println("if")
                       |  }
                       |}
                     """.stripMargin.replace("\r", "").trim

    doTest(text, resultText)
  }

  def testMergeIfToAnd4() {
    val text = """
                 |class MergeIfToAnd {
                 |  def mthd() {
                 |    val a: Int = 0
                 |    i<caret>f (a == 9)
                 |      if (a == 7) {
                 |        System.out.println("if")
                 |      }
                 |  }
                 |}
               """.stripMargin.replace("\r", "").trim
    val resultText = """
                       |class MergeIfToAnd {
                       |  def mthd() {
                       |    val a: Int = 0
                       |    i<caret>f (a == 9 && a == 7) {
                       |      System.out.println("if")
                       |    }
                       |  }
                       |}
                     """.stripMargin.replace("\r", "").trim

    doTest(text, resultText)
  }
}