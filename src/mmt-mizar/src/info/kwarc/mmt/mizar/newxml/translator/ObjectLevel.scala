package info.kwarc.mmt.mizar.newxml.translator

import info.kwarc.mmt.api._
import info.kwarc.mmt.api.objects._
import info.kwarc.mmt.lf._
import info.kwarc.mmt.lf.structuralfeatures.RecordUtil
import info.kwarc.mmt.mizar.newxml._
import info.kwarc.mmt.mizar.newxml.mmtwrapper.Mizar
import info.kwarc.mmt.mizar.newxml.mmtwrapper.Mizar._
import info.kwarc.mmt.mizar.newxml.mmtwrapper.PatternUtils._
import info.kwarc.mmt.mizar.newxml.translator.attributeTranslator.translate_Attribute
import info.kwarc.mmt.mizar.newxml.translator.claimTranslator.translate_Claim
import info.kwarc.mmt.mizar.newxml.translator.typeTranslator.translate_Type
import info.kwarc.mmt.mizar.newxml.translator.termTranslator.translate_Term
import syntax._
import translator.TranslatorUtils._
import translator.contextTranslator._
import translator.formulaTranslator._

object expressionTranslator {
  def translate_Expression(expr:Expression)(implicit defContent: DefinitionContext = DefinitionContext.empty()): Term = expr match {
    case tm: MizTerm => termTranslator.translate_Term(tm)
    case tp: Type => translate_Type(tp)
    case formula: Formula => translate_Formula(formula)
  }
}

object termTranslator {
  def translate_Term(tm:syntax.MizTerm)(implicit defContext: DefinitionContext = DefinitionContext.empty(), selectors: List[(Int, VarDecl)] = Nil) : Term = tm match {
    case Simple_Term(locVarAttr) =>
      val tr = TranslatorUtils.namedDefArgsSubstition(defContext.args)
      val refTm = LocalName(locVarAttr.toIdentifier(true))
      tr(refTm).getOrElse(OMV(refTm))
    case Aggregate_Term(tpAttrs, _args) =>
      val gn = computeGlobalPatternName(tpAttrs)
      val aggrDecl = referenceExtDecl(gn,RecordUtil.makeName)
      val args = translateArguments(_args)
      ApplyGeneral(aggrDecl, args)
    case Selector_Term(tpAttrs, _arg) =>
      val strGn = MMLIdtoGlobalName(tpAttrs.globalPatternName().copy(kind = "L"))
      val sel = referenceExtDecl(strGn, tpAttrs.spelling)
      val argument = translate_Term (_arg)
      Apply(sel, argument)
    case Circumfix_Term(tpAttrs, _symbol, _args) =>
      assert(tpAttrs.sort == "Functor-Term")
      val gn = computeGlobalPatternName(tpAttrs)
      val arguments = translateArguments(_args)
      ApplyGeneral(OMS(gn), arguments)
    case Numeral_Term(redObjAttr, nr, varnr) => num(nr)
    case itt @ it_Term(_, _) => OMV("it")
    case ist @ Internal_Selector_Term(redObjAttr, varnr) =>
      val nr = redObjAttr.nr
      val referencedSelector = utils.listmap(selectors, nr).getOrElse(
        throw new ObjectLevelTranslationError("The referenced selector with number "+nr+" is unknown, hence the internal selector term can't be translated. "+
          "\nThe only known selectors are: \n"+selectors.toString(), ist))
      referencedSelector.toTerm
    case Infix_Term(tpAttrs, infixedArgs) =>
      assert(tpAttrs.sort == "Functor-Term", "Expected Infix-Term to have sort Functor-Term, but instead found sort "+tpAttrs.sort)
      val gn = computeGlobalPatternName(tpAttrs)
      val args = translateArguments(infixedArgs._args)
      ApplyGeneral(OMS(gn), args)
    case Global_Choice_Term(pos, sort, _tp) =>
      val tp = translate_Type(_tp)
      Apply(constant("choice"), tp)
    case Placeholder_Term(redObjAttr, varnr) => OMV("placeholder_"+redObjAttr.nr.toString)
    case Private_Functor_Term(redObjAttr, serialNrIdNr, _args) => OMV(Utils.MizarVariableName(redObjAttr.spelling, redObjAttr.sort.stripSuffix("-Term"), serialNrIdNr))
    case Fraenkel_Term(pos, sort, _varSegms, _tm, _form) =>
      val tp : Type = _varSegms._vars.head._tp()
      val universe = translate_Type(tp)//getUniverse(tp)
      val arguments : List[OMV] = _varSegms._vars flatMap(translate_Context(_)) map(_.toTerm)
      val cond = translate_Formula(_form)
      val expr = translate_Term(_tm)
      fraenkelTerm(expr, arguments, universe, cond)
    case Simple_Fraenkel_Term(pos, sort, _varSegms, _tm) =>
      val tp : Type = _varSegms._vars.head._tp()
      val universe = translate_Type(tp)//getUniverse(tp)
      val arguments : List[OMV] = _varSegms._vars flatMap(translate_Context(_)) map(_.toTerm)
      val expr = translate_Term(_tm)
      simpleFraenkelTerm(expr, arguments, universe)
    case Qualification_Term(pos, sort, _tm, _tp) =>
      //TODO: do the required checks
      translate_Term(_tm)
    case Forgetful_Functor_Term(constrExtObjAttrs, _tm) =>
      val gn = computeGlobalPatternName(constrExtObjAttrs)
      val substr = OMS(gn)
      val struct = translate_Term(_tm)
      val structTm = TranslationController.simplifyTerm(struct)
      val ApplyGeneral(OMS(strAggrPath), aggrArgs) = structTm
      val strPath = strAggrPath.module ? strAggrPath.name.steps.init
      val restr = referenceExtDecl(strPath,structureDefRestrName(gn.name.toString)(strPath).toString)
      val arguments::argsTyped::_ = aggrArgs
      ApplyGeneral(restr, List(arguments, argsTyped, struct))
  }
}

object typeTranslator {
  def translate_Type_Specification(tp: Type_Specification) = translate_Type(tp._types)
  def translate_Type(tp:Type)(implicit selectors: List[(Int, VarDecl)] = Nil) : Term = tp match {
    case ReservedDscr_Type(idnr, nr, srt, _subs, _tp) => translate_Type(_tp)
    case Clustered_Type(pos, sort, _adjClust, _tp) =>
      val tp = translate_Type(_tp)
      val adjectives = _adjClust._attrs map translate_Attribute
      SimpleTypedAttrAppl(tp, adjectives)
    case Standard_Type(tpAttrs, noocc, origNr, _args) =>
      // Seems to roughly correspond to an OMS referencing a type, potentially applied to some arguments
      // TODO: Check this is the correct semantics and take care of the noocc attribute
      val gn = computeGlobalPatternName(tpAttrs)
      val tp : Term = OMS(gn)
      val args = translateArguments(_args)
      ApplyGeneral(tp,args)
    case Struct_Type(tpAttrs, _args) =>
      val gn = computeGlobalPatternName(tpAttrs)
      val typeDecl = referenceExtDecl(gn,RecordUtil.recTypeName)
      val args = translateArguments(_args)
      ApplyGeneral(typeDecl, args)
  }
}

object formulaTranslator {
  def translate_Formula(formula:Formula)(implicit defContext: DefinitionContext = DefinitionContext.empty()) : Term = formula match {
    case Existential_Quantifier_Formula(pos, sort, _vars, _restrict, _expression) =>
      val tp : Type = _vars._vars.head._tp()
      val univ = translate_Type(tp)
      val vars = translateVariables(_vars)
      val expr = translate_Claim(_expression)
      val assumptions = translate_Restriction(_restrict)
      translate_Existential_Quantifier_Formula(vars, univ, expr, assumptions)
    case Relation_Formula(objectAttrs, antonymic, infixedArgs) =>
      if (antonymic.isDefined && antonymic.get) {
        translate_Formula(negatedFormula(Relation_Formula(objectAttrs, None, infixedArgs)))
      } else {
        val rel = translateObjRef(objectAttrs)
        val args = translateArguments(infixedArgs._args)
        ApplyGeneral(rel, args)
      }
    case Universal_Quantifier_Formula(pos, sort, _vars, _restrict, _expression) =>
      val tp : Type = _vars._vars.head._tp()
      val univ = translate_Type(tp)
      val vars = translateVariables(_vars)
      val expr = translate_Claim(_expression)
      val assumptions = translate_Restriction(_restrict)
      translate_Universal_Quantifier_Formula(vars, univ, expr, assumptions)
    case Multi_Attributive_Formula(pos, sort, _tm, _cluster) =>
      val tm = termTranslator.translate_Term(_tm)
      val attrs = _cluster._attrs map translate_Attribute
          and(attrs.map(at => Apply(at, tm)))
    case Conditional_Formula(pos, sort, _frstFormula, _sndFormula) =>
      val assumption = translate_Claim(_frstFormula)
      val conclusion = translate_Claim(_sndFormula)
      implies(assumption, conclusion)
    case Conjunctive_Formula(pos, sort, _frstConjunct, _sndConjunct) =>
      val frstConjunct = translate_Claim(_frstConjunct)
      val sndConjunct = translate_Claim(_sndConjunct)
      binaryAnd(frstConjunct, sndConjunct)
    case Biconditional_Formula(pos, sort, _frstFormula, _sndFormula) =>
      val frstForm = translate_Claim(_frstFormula)
      val sndForm = translate_Claim(_sndFormula)
      iff(frstForm, sndForm)
    case Disjunctive_Formula(pos, sort, _frstDisjunct, _sndDisjunct) =>
      val frstDisjunct = translate_Claim(_frstDisjunct)
      val sndDisjunct = translate_Claim(_sndDisjunct)
      binaryOr(frstDisjunct, sndDisjunct)
    case Negated_Formula(pos, sort, _formula) =>
      Apply(constant("not"),translate_Claim(_formula))
    case Contradiction(pos, sort) => constant("contradiction")
    case Qualifying_Formula(pos, sort, _tm, _tp) => is(translate_Term(_tm), translate_Type(_tp))
    case Private_Predicate_Formula(redObjAttr, serialNrIdNr, constrNr, _args) =>
      OMV(Utils.MizarVariableName(redObjAttr.spelling, redObjAttr.sort.stripSuffix("-Formula"), serialNrIdNr))
    case FlexaryDisjunctive_Formula(pos, sort, _formulae) =>
      val formulae = _formulae map translate_Claim
      or(formulae)
    case FlexaryConjunctive_Formula(pos, sort, _formulae) =>
      val formulae = _formulae map translate_Claim
      and(formulae)
    case Multi_Relation_Formula(pos, sort, _relForm, _rhsOfRFs) => ???
  }
  def translate_Existential_Quantifier_Formula(vars:Context, expression:Term, assumptions: Option[Claim])(implicit args: Context): Term = vars.variables match {
    case Nil => assumptions match {
      case Some(ass) => implies(translate_Claim(ass), expression)
      case None => expression
    }
    case v::vs =>
      val expr = translate_Existential_Quantifier_Formula(vs, expression, assumptions)
      exists(v.toTerm, v.tp.get,expr)
  }
  def translate_Existential_Quantifier_Formula(vars:List[OMV], univ:Term, expression:Term, assumptions: Option[Claim] = None)(implicit args: Context=Context.empty): Term = {
    val ctx = vars map (_ % univ)
    translate_Existential_Quantifier_Formula(ctx, expression, assumptions)
  }
  def translate_Universal_Quantifier_Formula(vars:List[OMV], univ:Term, expression:Term, assumptions: Option[Claim] = None)(implicit args: Context=Context.empty): Term = vars match {
    case Nil => assumptions map translate_Claim map(implies(_, expression)) getOrElse expression
    case v::vs =>
      val expr = translate_Universal_Quantifier_Formula(vs, univ, expression, assumptions)
      forall(v,univ,expr)
  }
  def translate_Restriction(maybeRestriction: Option[Restriction]) = maybeRestriction map {
    case Restriction(_formula) => _formula
  }
}

object contextTranslator {
  def translate_Variable(variable:Variable, localId: Boolean = true) : OMV = {
    OMV(variable.varAttr.toIdentifier(localId))
  }
  private def translateSingleTypedVariable(_var : Variable, _tp: Type)(implicit selectors: List[(Int, VarDecl)] = Nil) = {
    val variable = translate_Variable(_var)
    val tp = translate_Type(_tp)
    variable % tp
  }
  def translate_Context(varSegm: VariableSegments)(implicit selectors: List[(Int, VarDecl)] = Nil) : Context= varSegm match {
    case Free_Variable_Segment(pos, _var, _tp) => translateSingleTypedVariable(_var, _tp)
    case Implicitly_Qualified_Segment(pos, _var, _tp) =>translateSingleTypedVariable(_var, _tp)
    case Explicitly_Qualified_Segment(pos, _variables, _tp) => _variables._vars.map(v => translateSingleTypedVariable(v,_tp))
  }
  def translateVariables(varSegms: VariableSegments) : List[OMV] = {varSegms._vars().map(translate_Variable(_))}
  def translateVariables(varSegms: Variable_Segments) : List[OMV] = {getVariables(varSegms).map(translate_Variable(_))}
  def translateVariables(segm: Segments) : Context = {
    val vs = segm._vars._vars.map {
      v =>
        translate_Variable(v, true)}
    val argTypes = segm._tpList._tps map translate_Type
    val ret = segm match {
      case Functor_Segment(pos, _vars, _tpList, _tpSpec) => _tpSpec map(_._types) map translate_Type get
      case Predicate_Segment(pos, _vars, _tpList) => prop
    }
    val tp = Arrow(argTypes, ret)
    vs map (_ % tp)
  }
	def translate_Locus(loc:Locus) : OMV = {
		OMV(loc.varAttr.toIdentifier())
	}
}

object claimTranslator {
  def translate_Claim(claim:Claim)(implicit defContext: DefinitionContext = DefinitionContext.empty()) : Term = claim match {
    case Assumption(_ass) => translate_Assumption(_ass)
    case ass: Assumptions => translate_Assumption(ass)
    case form: Formula => translate_Formula(form)
    case Proposition(pos, _label, _thesis) => translate_Claim(_thesis)
    case Thesis(pos, sort) => ???
    case Diffuse_Statement(spell, serialnr, labelnr, _label) => ???
    case Conditions(_props) => and(_props map translate_Claim)
    case Iterative_Equality(_label, _formula, _just, _iterSteps) => ???
  }
  def translate_Loci_Equality(loci_Equality: Loci_Equality)(implicit defContext: DefinitionContext) : Term = {
    val List(a, b) = List(loci_Equality._frstLocus, loci_Equality._sndLocus) map translate_Locus
    assert(defContext.args.variables.map(_.toTerm).contains(a) && defContext.args.variables.map(_.toTerm).contains(b))
    Mizar.eq(a, b)
  }
  def translate_Loci_Equalities(loci_Equalities: Loci_Equalities)(implicit defContext: DefinitionContext) : List[Term] = {
    loci_Equalities._lociEqns map translate_Loci_Equality
  }
  def translate_Assumption(ass: Assumptions)(implicit defContext: DefinitionContext = DefinitionContext.empty()) : Term = ass match {
    case Collective_Assumption(pos, _cond) => translate_Claim(_cond)
    case Existential_Assumption(_qualSegm, _cond) =>
      val args = _qualSegm._children flatMap translate_Context
      val cond = translate_Claim(_cond)
      translate_Existential_Quantifier_Formula(args, cond, None)(defContext.args)
    case Single_Assumption(pos, _prop) => translate_Claim(_prop)
  }
}

object attributeTranslator {
  def translateAttributes(adjective_Cluster: Adjective_Cluster) = adjective_Cluster._attrs map translate_Attribute
  def translate_Attribute(attr: Attribute): Term = {
    val gn = computeGlobalPatternName(attr.orgnlExtObjAttrs)
    val args = translateArguments(attr._args)
    ApplyGeneral(OMS(gn), args)
  }
}