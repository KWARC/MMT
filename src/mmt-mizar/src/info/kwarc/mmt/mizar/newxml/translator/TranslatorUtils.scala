package info.kwarc.mmt.mizar.newxml.translator

import info.kwarc.mmt.api.symbols.OMSReplacer
import info.kwarc.mmt.api.{objects, _}
import info.kwarc.mmt.lf.elpi.ELPI.Lambda
import info.kwarc.mmt.lf.{Pi, Univ}
import notations.NotationContainer
import objects._
import info.kwarc.mmt.mizar.newxml._
import info.kwarc.mmt.mizar.newxml.mmtwrapper.MMTUtils.Lam
import info.kwarc.mmt.mizar.newxml.mmtwrapper.MizSeq._
import info.kwarc.mmt.mizar.newxml.mmtwrapper.Mizar
import info.kwarc.mmt.mizar.newxml.mmtwrapper.PatternUtils._
import syntax.Utils.MizarGlobalName
import syntax._
import info.kwarc.mmt.mizar.newxml.translator.contextTranslator.translate_Variable
import termTranslator._

sealed class TranslatingError(str: String) extends Exception(str)
class DeclarationLevelTranslationError(str: String, decl: DeclarationLevel) extends TranslatingError(str)
case class DeclarationTranslationError(str: String, decl: Subitem) extends DeclarationLevelTranslationError(str, decl) {
  def apply(str: String, decl: Subitem) = {
    new DeclarationLevelTranslationError(str+
      "\nDeclarationTranslationError while translating the "+decl.shortKind+": "+decl.toString, decl)
  }
}
class ObjectLevelTranslationError(str: String, tm: ObjectLevel) extends TranslatingError(str)
case class ProvedClaimTranslationError(str: String, prfedClaim: ProvedClaim) extends ObjectLevelTranslationError(str+
  "\nProvedClaimTranslationError while translating the (proved) "+prfedClaim._claim.getClass.getName+": "+prfedClaim.toString, prfedClaim)
case class PatternTranslationError(str: String, pat: Patterns) extends ObjectLevelTranslationError(str+
  "\nPatternClaimTranslationError while translating the pattern with spelling "+pat.patternAttrs.spelling+": "+pat.toString, pat)
case class ExpressionTranslationError(str: String, expr: Expression) extends ObjectLevelTranslationError(str+
  "\nExpressionTranslationError while translating the expression "+expr.ThisType()+": "+expr.toString, expr)

object TranslatorUtils {
  def makeGlobalName(aid: String, kind: String, nr: Int) : info.kwarc.mmt.api.GlobalName = {
    val ln = LocalName(kind+nr)
    TranslationController.getTheoryPath(aid) ? ln
  }
  def makeGlobalPatConstrName(patAid: String, constrAid: String, kind: String, patNr: Int, constrNr: Int) : info.kwarc.mmt.api.GlobalName = {
    val patGN = makeGlobalName(patAid, kind, patNr)
    val constrGN = makeGlobalName(constrAid, kind, constrNr)
    constrGN.copy(name = LocalName(patGN.name.toString + constrGN.name.toString))
  }
  def makeNewGlobalName(kind: String, nr: Int) = makeGlobalName(TranslationController.currentAid, kind, nr)

  def MMLIdtoGlobalName(mizarGlobalName: MizarGlobalName): info.kwarc.mmt.api.GlobalName = {
    makeGlobalName(mizarGlobalName.aid, mizarGlobalName.kind, mizarGlobalName.nr)
  }
  private def computeGlobalPatternName(tpAttrs: globallyReferencingObjAttrs) = {MMLIdtoGlobalName(tpAttrs.globalPatternName())}
  private def computeGlobalConstrName(tpAttrs: globallyReferencingDefAttrs) = {MMLIdtoGlobalName(tpAttrs.globalConstrName())}
  private def computeGlobalOrgPatternName(tpAttrs: globallyReferencingReDefAttrs) = {MMLIdtoGlobalName(tpAttrs.globalOrgPatternName())}
  private def computeGlobalOrgConstrName(tpAttrs: globallyReferencingReDefAttrs) = {MMLIdtoGlobalName(tpAttrs.globalOrgConstrName())}
  private def computeGlobalPatConstrName(tpAttrs: globallyReferencingDefAttrs) = {makeGlobalPatConstrName(tpAttrs.globalPatternFile, tpAttrs.globalConstrFile, tpAttrs.globalKind, tpAttrs.globalPatternNr, tpAttrs.globalConstrNr)}
  private def computeGlobalOrgPatConstrName(tpAttrs: globallyReferencingReDefAttrs) = {makeGlobalPatConstrName(tpAttrs.globalOrgPatternFile, tpAttrs.globalOrgConstrFile, tpAttrs.globalKind, tpAttrs.globalOrgPatternNr, tpAttrs.globalOrgConstrNr)}
  def computeGlobalName(pat: globallyReferencingObjAttrs, orgName: Boolean = false) = pat match {
    case p: RedefinablePatterns => if (orgName) computeGlobalOrgPatConstrName(p) else computeGlobalPatConstrName(p)
    case p: ConstrPattern => computeGlobalPatConstrName(p)
    case objAttrs: globallyReferencingReDefAttrs => if (orgName) computeGlobalOrgPatConstrName(objAttrs) else computeGlobalPatConstrName(objAttrs)
    case objAttrs: globallyReferencingDefAttrs => computeGlobalPatConstrName(objAttrs)
    case objAttrs => computeGlobalPatternName(objAttrs)
  }
  def addConstant(gn:info.kwarc.mmt.api.GlobalName, notC:NotationContainer, df: Option[Term], tp:Option[Term] = None) = {
    val hm : Term= OMMOD(gn.module).asInstanceOf[Term]
    val const = info.kwarc.mmt.api.symbols.Constant(OMMOD(gn.module), gn.name, Nil, tp, df, None, notC)
    TranslationController.add(const)
  }
  def emptyPosition() = syntax.Position("translation internal")
  def negatedFormula(form:Claim) = Negated_Formula(emptyPosition(),"Negated-Formula",form)
  def emptyCondition() = negatedFormula(Contradiction(emptyPosition(),"Contradiction"))

  def getVariables(varSegms: Variable_Segments) : List[Variable] = varSegms._vars.flatMap {
    case segm: VariableSegments => segm._vars()
  }

  val hiddenArt = TranslationController.getTheoryPath("hidden")
  val hiddenArts = List("hidden", "tarski", "tarski_a") map TranslationController.getTheoryPath

  def resolveHiddenReferences() = {
    OMSReplacer({gn: GlobalName =>
      val hiddenModule = hiddenArt
      gn match {
      case GlobalName(hiddenModule, name) => Mizar.translate_hidden(name)
      case _ => None
    }})
  }
  /**
   * Compute a substitution substituting the implicitely sublied argument by terms of the form OMV(<varName> / i),
   * where <varName> is the name of the argument sequence from the corresponding pattern
   *
   * This is make the arguments used the names actually used in the binder of the pattern,
   * not the names given to the arguments in Mizar
   * @param varName (default "x") The name of the argument sequence in the corresponding pattern
   * @param args (implicit) the arguments to build the substitution for
   * @return
   */
  def namedDefArgsSubstition(args: Context, varName: String = "x") = {
    val (argNum, argTps) = (args.length, args map (_.toTerm))
    objects.Substitution(argTps.zipWithIndex map {
      case (vd, i) => vd / OMV(LocalName(varName)/ i.toString)
    }:_*)
  }
  /**
   * Compute a translator substituting the implicitely sublied argument within type and definition of a declaration by terms of the form OMV(<varName> / i),
   * where <varName> is the name of the argument sequence from the corresponding pattern
   *
   * This is make the arguments use the names actually used in the binder of the pattern,
   * not the names given to the arguments in Mizar
   * @param varName (default "x") The name of the argument sequence in the corresponding pattern
   * @param args (implicit) the arguments to build the substitution for
   * @return A translation function on declarations making the substitution
   */
  private def namedDefArgsTranslator(varName: String, args: Context) : symbols.Declaration => symbols.Declaration = {
    {d: symbols.Declaration =>
      val tl = symbols.ApplySubs(namedDefArgsSubstition(args, varName)).compose(resolveHiddenReferences().toTranslator())
     d.translate(tl, Context.empty)}
  }
  def namedDefArgsTranslator(varName: String = "x")(implicit defContext: DefinitionContext) : symbols.Declaration => symbols.Declaration = namedDefArgsTranslator(varName, defContext.args)
  def translateArguments(arguments: Arguments)(implicit args: Context = Context.empty, assumptions: List[Term] = Nil, corr_conds: List[JustifiedCorrectnessConditions] = Nil, props: List[Property] = Nil, selectors: List[(Int, VarDecl)] = Nil) : List[Term] = {arguments._children map translate_Term }
  def translateObjRef(refObjAttrs:globallyReferencingObjAttrs)  = OMS(computeGlobalPatternName(refObjAttrs))
}