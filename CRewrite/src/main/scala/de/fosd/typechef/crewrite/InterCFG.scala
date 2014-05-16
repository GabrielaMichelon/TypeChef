package de.fosd.typechef.crewrite

import de.fosd.typechef.featureexpr.FeatureExpr
import de.fosd.typechef.conditional.{Opt, ConditionalMap, Conditional}
import de.fosd.typechef.parser.c._
import scala.Some
import de.fosd.typechef.crewrite.asthelper.{CASTEnv, ASTEnv}

/**
 * interprocedural control flow graph (cfg) implementation based on the
 * intraprocedural cfg implementation (see IntraCFG.scala)
 * To do so, we resolve function calls and add edges to function definitions
 * to our resulting list.
 *
 * In addition, we build a call graph which is a smaller abstraction and contains
 * only edges between functions (multiple edges can be unified)
 *
 * ChK: search for function calls. we will never be able to be precise here, but we can detect
 * standard function calls "a(...)" at least. The type system will also detect types of parameters
 * and pointers, but that should not be necessary (with pointers we won't get the precise target
 * anyway without expensive dataflow analysis and parameters do not matter since C has no overloading)
 */

trait InterCFG extends IntraCFG {

    // provide a lookup mechanism for function defs (from the type system or selfimplemented)
    // return None if function cannot be found
    def getTranslationUnit(): TranslationUnit

    private var functionDefMap: ConditionalMap[String, Option[ExternalDef]] = new ConditionalMap[String, Option[ExternalDef]]
    private var functionFExpr: Map[ExternalDef, FeatureExpr] = Map()

    val callGraph = new CallGraph()

    assert(getTranslationUnit != null)
    for (Opt(f, externalDef) <- getTranslationUnit().defs) {
            functionFExpr = functionFExpr + (externalDef -> f)
            externalDef match {
                case FunctionDef(_, decl, _, _) =>
                functionDefMap = functionDefMap +(decl.getName, f, Some(externalDef))
                case Declaration(_, initDecls) =>
                    for (Opt(fi, initDecl) <- initDecls) {
                    functionDefMap = functionDefMap +(initDecl.getName, f and fi, Some(externalDef))
                    }
                case _ =>
            }
        callGraph.addNode(externalDef, f)
        }


    def externalDefFExprs = functionFExpr
    def lookupFunctionDef(name: String): Conditional[Option[ExternalDef]] = {
        functionDefMap.getOrElse(name, None)
    }

    override private[crewrite] def findMethodCalls(t: AST, env: ASTEnv, oldres: CFGRes, ctx: FeatureExpr, _res: CFGRes): CFGRes = {
        var res: CFGRes = _res
        val postfixExprs = filterAllASTElems[PostfixExpr](t)
        for (pf@PostfixExpr(Id(funName), FunctionCall(_)) <- postfixExprs) {
            val fexpr = env.featureExpr(pf)
            val newresctx = getNewResCtx(oldres, ctx, fexpr)
            val targetFun = lookupFunctionDef(funName)
            targetFun.mapf(fexpr, {
                case (f, Some(target)) =>
                    val thisFun = findPriorASTElem[ExternalDef](t, env)
                    thisFun.map(callGraph.addEdge(_, target, newresctx and f))
                    res = (newresctx and f, f, target) :: res
                case _ =>
            })
        }
        res
    }

    override def getExprSucc(exp: Expr, ctx: FeatureExpr, oldres: CFGRes, env: ASTEnv): CFGRes = {
        findMethodCalls(exp, env, oldres, ctx, oldres) ++ super.getExprSucc(exp, ctx, oldres, env)
    }
}
class InterCFGProducer(translationUnit: TranslationUnit) extends InterCFG with CFGHelper {
    override def getTranslationUnit(): TranslationUnit = translationUnit

    /**
     * get a full CFG for the the translation unit
     * creates a call graph as a side effect
     */
    def getInterCFG(): List[SuccessorRelationship] = {
        val env = CASTEnv.createASTEnv(translationUnit)
        val fdefs = filterAllASTElems[FunctionDef](translationUnit)
        fdefs.map(getAllSucc(_, env))
    }

    /**
     * return the call graph after computing the full interCFG
     */
    def generateCallGraph(): CallGraph = { getInterCFG(); callGraph }
}
