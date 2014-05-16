package de.fosd.typechef.crewrite

import org.junit.Test
import org.scalatest.matchers.ShouldMatchers
import de.fosd.typechef.parser.c._
import scala.Predef._
import de.fosd.typechef.typesystem.{CTypeCache, CDeclUse, CTypeSystemFrontend}
import de.fosd.typechef.crewrite.asthelper.EnforceTreeHelper

class CFGInNonVoidFuncTest extends TestHelper with ShouldMatchers with CFGHelper {

    def cfgInNonVoidFunc(code: String): Boolean = {
        val tunit = EnforceTreeHelper.prepareAST[TranslationUnit](parseTranslationUnit(code))
        val ts = new CTypeSystemFrontend(tunit) with CTypeCache with CDeclUse
        assert(ts.checkASTSilent, "typecheck fails!")
        val cf = new CIntraAnalysisFrontend(tunit, ts)
        cf.cfgInNonVoidFunc()
    }

    @Test def test_cfgInNonVoidFunc() {
        cfgInNonVoidFunc( """
               int f(void) {
                  int a;
                  switch (a) {
                    a = a+1;
                    case 0: a+2;
                    default: a+3;
                  }
               }
        """.stripMargin) should be(false)

        cfgInNonVoidFunc( """
                   void f(void) {
                      int a;
                      switch (a) {
                        case 0: a+2;
                        default: a+3;
                      }
                   }
        """.stripMargin) should be(true)

        cfgInNonVoidFunc( """
                   #ifdef A
                   void
                   #else
                   int
                   #endif
                   f(void) {
                      int a;
                      #ifndef A
                      return a;
                      #endif
                   }
                            """.stripMargin) should be(true)
    }

    @Test def test_securecoding {
        cfgInNonVoidFunc(
            """
            int foo(int x) {
               if (x > 0) {
                  return 1;
               }
            }

            void bar(int y) {
              if (foo(y+2)) {
                // ...
              }
            }
            """.stripMargin) should be(false)
    }
}

