package de.fosd.typechef.crewrite

import org.junit.Test
import org.scalatest.matchers.ShouldMatchers
import de.fosd.typechef.parser.c._
import scala.Predef._
import de.fosd.typechef.crewrite.asthelper.EnforceTreeHelper

class DanglingSwitchCodeTest extends TestHelper with ShouldMatchers with CFGHelper  {

    def danglingSwitchCode(code: String): Boolean = {
        val tunit = EnforceTreeHelper.prepareAST[TranslationUnit](parseTranslationUnit(code))
        val ds = new CIntraAnalysisFrontend(tunit, null)
        ds.danglingSwitchCode()
    }

    @Test def test_danglingswitch_simple() {
        danglingSwitchCode( """
               void f(void) {
                  int a;
                  switch (a) {
                    a = a+1;
                    case 0: a+2;
                    default: a+3;
                  }
               }
        """.stripMargin) should be(false)

        danglingSwitchCode( """
               void f(void) {
                  int a;
                  switch (a) {
                    case 0: a+2;
                    default: a+3;
                  }
               }
        """.stripMargin) should be(true)
    }

    // The analysis does not cover unreachable code in general!
    @Test def test_statement_after_break() {
        danglingSwitchCode( """
               void f(void) {
                  int a;
                  switch (a) {
                    case 0: a+2;
                    default: a+3;
                    a++;  // unreachable code
                  }
               }
                            """.stripMargin) should be(true)
    }

    @Test def test_variable_statements() {
        danglingSwitchCode( """
               void f(void) {
                  int a;
                  switch (a) {
                    #ifdef A
                    a++;
                    #endif
                    case 0: a+2;
                    default: a+3;
                  }
               }
        """.stripMargin) should be(false)

        danglingSwitchCode( """
               void f(void) {
                  int a;
                  #ifdef A
                  switch (a) {
                    #ifndef A
                    a++;
                    #endif
                    case 0: a+2;
                    default: a+3;
                  }
                  #endif
               }
        """.stripMargin) should be(true)
    }


}

