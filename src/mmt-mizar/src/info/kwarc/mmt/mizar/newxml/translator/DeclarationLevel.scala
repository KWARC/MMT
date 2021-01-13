package info.kwarc.mmt.mizar.newxml.translator

import info.kwarc.mmt.api.notations.NotationContainer
import info.kwarc.mmt.api._
import info.kwarc.mmt.api.objects.VarDecl
import info.kwarc.mmt.lf._
import info.kwarc.mmt.mizar.newxml.mmtwrapper._
import info.kwarc.mmt.mizar.newxml.syntax._
import info.kwarc.mmt.mizar.newxml.translator._
import expressionTranslator._
import justificationTranslator._
import termTranslator._
import typeTranslator._
import contextTranslator._
import formulaTranslator._

object subitemTranslator {
  def translate_Reservation(reservation: Reservation) = { Nil }
  def translate_Definition_Item(definition_Item: Definition_Item) = {
    definition_Item.check() match {
      case "Definitional-Block" => blockTranslator.translate_Definitional_Block(definition_Item._block)
      case "Registration-Block" => blockTranslator.translate_Registration_Block(definition_Item._block)
    }


  }
  def translate_Section_Pragma(section_Pragma: Section_Pragma) = { Nil }
  def translate_Pragma(pragma: Pragma) = { ??? }
  def translate_Loci_Declaration(loci_Declaration: Loci_Declaration): List[(Option[LocalName], objects.Term)] = {
    val varContext = loci_Declaration._qualSegms._children flatMap(translate_Context(_))
    varContext map {vd => (Some(vd.name), vd.tp.get)}
  }
  def translate_Cluster(cluster: Cluster) = { ??? }
  def translate_Correctness(correctness: Correctness) = { ??? }
  def translate_Correctness_Condition(correctness_Condition: Correctness_Condition) = { ??? }
  def translate_Exemplification(exemplification: Exemplification) = { ??? }
  def translate_Assumption(assumption: Assumption) = { ??? }
  def translate_Identify(identify: Identify) = { ??? }
  def translate_Generalization(generalization: Generalization) = { ??? }
  def translate_Reduction(reduction: Reduction) = { ??? }
  def translate_Scheme_Block_Item(scheme_Block_Item: Scheme_Block_Item) = { ??? }
  def translate_Property(property: Property) = { ??? }
  def translate_Per_Cases(per_Cases: Per_Cases) = { ??? }
  def translate_Case_Block(case_block: Case_Block) = { ??? }
}

object headTranslator {
  def translate_Head(head:Heads) = { ??? }
  def translate_Scheme_Head(reservation: Scheme_Head) = {}
  def translate_Suppose_Head(reservation: Suppose_Head) = {}
  def translate_Case_Head(reservation: Case_Head) = {}
}

object nymTranslator {
  def translate_Nym(nym:Nyms) = { ??? }
  def translate_Pred_Antonym(pred_Antonym: Pred_Antonym) = {}
  def translate_Pred_Synonym(pred_Synonym: Pred_Synonym) = {}
  def translate_Attr_Synonym(attr_Synonym: Attr_Synonym) = {}
  def translate_Attr_Antonym(attr_Antonym: Attr_Antonym) = {}
  def translate_Func_Synonym(func_Synonym: Func_Synonym) = {}
  def translate_Func_Antonym(func_Antonym: Func_Antonym) = {}
  def translate_Mode_Synonym(mode_Synonym: Mode_Synonym) = {}
}

object statementTranslator {
  def translate_Statement(st:Statement) = st match {
    case conclusion: Conclusion => translate_Conclusion(conclusion)
    case type_Changing_Statement: Type_Changing_Statement => translate_Type_Changing_Statement(type_Changing_Statement)
    case theorem_Item: Theorem_Item => translate_Theorem_Item(theorem_Item)
    case choice_Statement: Choice_Statement => translate_Choice_Statement(choice_Statement)
    case regular_Statement: Regular_Statement => translate_Regular_Statement(regular_Statement)
  }
  def translate_Conclusion(conclusion: Conclusion) = { ??? }
  def translate_Type_Changing_Statement(type_Changing_Statement: Type_Changing_Statement) = { ??? }
  def translate_Theorem_Item(reservation: Theorem_Item) = { ??? }
  def translate_Choice_Statement(reservation: Choice_Statement) = { ??? }
  def translate_Regular_Statement(regular_Statement: Regular_Statement) = { ??? }
}

object definitionTranslator {
  def translate_Definition(defn:Definition)(implicit args: List[(Option[LocalName], objects.Term)]= Nil) : List[info.kwarc.mmt.api.symbols.Declaration] = defn match {
    case at: Attribute_Definition => translate_Attribute_Definition(at)
    case cd: Constant_Definition => translate_Constant_Definition(cd)
    case funcDef: Functor_Definition => translate_Functor_Definition(funcDef)
    case md: Mode_Definition => translate_Mode_Definition(md)
    case pd: Predicate_Definition => translate_Predicate_Definition(pd)
    case d: Private_Functor_Definition  => translate_Private_Functor_Definition(d)
    case d: Private_Predicate_Definition => translate_Private_Predicate_Definition(d)
    case d: Structure_Definition => translate_Structure_Definition(d)
  }
  def translate_Structure_Definition(strDef: Structure_Definition)(implicit args: List[(Option[LocalName], objects.Term)]= Nil): List[symbols.Declaration] = {
    val l = args.length
    implicit var selectors: List[(Int, VarDecl)] = Nil
    val substr: List[objects.Term] = strDef._ancestors._structTypes.map(translate_Type) map {
      case ApplyGeneral(typeDecl, args) => typeDecl
    }
    val n = substr.length
    var substitutions : List[objects.Sub] = Nil
    val patternNr = strDef._strPat.patDef.globalDefAttrs.globalPatternNr
    val declarationPath = TranslatorUtils.makeNewGlobalName("Struct-Type", patternNr)

    def translate_Field_Segments(field_Segments: Field_Segments)(implicit args: List[(Option[LocalName], objects.Term)]= Nil) : List[VarDecl] = field_Segments._fieldSegments flatMap {
      case field_Segment: Field_Segment =>
			val tp = translate_Type(field_Segment._tp)
      field_Segment._selectors._loci.reverse map { case selector =>
        val selName = translate_Locus(selector._loci)
        val sel = (selector.posNr.nr.nr, selName % tp)
        selectors ::= sel
        substitutions ::= selName / PatternUtils.referenceExtDecl(declarationPath, selName.name.toString)
        sel._2 ^ substitutions
      }
    }
    val fieldDecls = translate_Field_Segments(strDef._fieldSegms)
    val m = fieldDecls.length
    StructureInstance(declarationPath, l, args, n, substr, m, fieldDecls)
  }
  def translate_Attribute_Definition(attribute_Definition: Attribute_Definition)(implicit args: List[(Option[LocalName], objects.Term)]= Nil) = attribute_Definition match {
    case atd @ Attribute_Definition(a, _redef, _attrPat, _def) =>
      val gn = TranslatorUtils.MMLIdtoGlobalName(atd.mizarGlobalName())
      val name = gn.name.toString
      val defn = _def.map(definiensTranslator.translate_Definiens(_))
      if (defn.isEmpty) {
        if(_redef.occurs) {

          val origDecl = TranslatorUtils.MMLIdtoGlobalName(_attrPat.globalPatternName())
          val oldDef = TranslationController.controller.get(origDecl) match {case decl: symbols.Constant => decl}
          val redef = TranslationController.makeConstant(gn.name, oldDef.tp, oldDef.df)
          List(redef)
        } else {
          ???
        }
      }
      val motherTp = TranslationController.inferType(defn.get.someCase)
      val (argNum, argTps) = (args.length, args map (_._2))
      val atrDef = defn.get match {
        case DirectPartialCaseByCaseDefinien(cases, caseRes, defRes) => directPartialAttributeDefinitionInstance(name, argNum, argTps, motherTp, defn.get.caseNum, cases, caseRes, defRes)
        case IndirectPartialCaseByCaseDefinien(cases, caseRes, defRes) => indirectPartialAttributeDefinitionInstance(name, argNum, argTps, motherTp, defn.get.caseNum, cases, caseRes, defRes)
        case DirectCompleteCaseByCaseDefinien(cases, caseRes, completenessProof) => directCompleteAttributeDefinitionInstance(name, argNum, argTps, motherTp, defn.get.caseNum, cases, caseRes)
        case IndirectCompleteCaseByCaseDefinien(cases, caseRes, completenessProof) => indirectCompleteAttributeDefinitionInstance(name, argNum, argTps, motherTp, defn.get.caseNum, cases, caseRes)
      }
      List(atrDef)
  }
  def translate_Constant_Definition(constant_Definition: Constant_Definition)(implicit args: List[(Option[LocalName], objects.Term)]= Nil) = { ??? }
  def translate_Functor_Definition(functor_Definition: Functor_Definition)(implicit args: List[(Option[LocalName], objects.Term)]= Nil) = functor_Definition match {
    case fd @ Functor_Definition(_, _redefine, _pat, _tpSpec, _def) =>
  val gn = TranslatorUtils.MMLIdtoGlobalName(fd.mizarGlobalName())
    val name = gn.name.toString
    val specType = _tpSpec map (tpSpec => translate_Type(tpSpec._types))
    val defn = _def.map(definiensTranslator.translate_Definiens(_))
    val ret = if (specType.isDefined) {specType} else { defn.map(d => TranslationController.inferType(d.someCase)) }
    if (defn.isEmpty) {
      if(_redefine.occurs) {
        //Redefinitions are only possible for Infix or Circumfix Functors
        val orgExtPatAttrs = _pat.patDef
        val origDecl = TranslatorUtils.computeGlobalOrgPatternName(orgExtPatAttrs)
        val oldDef = TranslationController.controller.get(origDecl) match {case decl: symbols.Constant => decl}
        val redef = TranslationController.makeConstant(gn.name, Some(ret getOrElse oldDef.tp.get), oldDef.df)
        List(redef)
      } else {
        ???
      }
    }
    val (argNum, argTps) = (args.length, args map (_._2))
    val funcDef = defn.get match {
      case DirectPartialCaseByCaseDefinien(cases, caseRes, defRes) => directPartialFunctorDefinition(name, argNum, argTps, ret.get, defn.get.caseNum, cases, caseRes, defRes)
      case IndirectPartialCaseByCaseDefinien(cases, caseRes, defRes) => indirectPartialFunctorDefinition(name, argNum, argTps, ret.get, defn.get.caseNum, cases, caseRes, defRes)
      case DirectCompleteCaseByCaseDefinien(cases, caseRes, completenessProof) => directCompleteFunctorDefinition(name, argNum, argTps, ret.get, defn.get.caseNum, cases, caseRes)
      case IndirectCompleteCaseByCaseDefinien(cases, caseRes, completenessProof) => indirectCompleteFunctorDefinition(name, argNum, argTps, ret.get, defn.get.caseNum, cases, caseRes)
    }
    List(funcDef)
  }
  def translate_Mode_Definition(mode_Definition: Mode_Definition)(implicit args: List[(Option[LocalName], objects.Term)]= Nil) = {
    val patternNr = mode_Definition._pat.patDef.patAttr.patternnr.patternnr
    val declarationPath = TranslatorUtils.makeNewGlobalName("Mode", patternNr)
    mode_Definition._expMode match {
      case Expandable_Mode(_tp) =>
        val tp = translate_Type(_tp)
        List(TranslationController.makeConstant(declarationPath.name, Some(Mizar.tp),Some(tp)))
      case stm @ Standard_Mode(_tpSpec, _def) =>
        val name = declarationPath.name.toString
        val (argNum, argTps) = (args.length, args map (_._2))
        val defnO = _def map(definiensTranslator.translate_Definiens(_))
        if (defnO.isEmpty) {
          assert(_tpSpec.isDefined)
          List(TranslationController.makeConstant(declarationPath.name, translate_Type(_tpSpec.get._types)))
        }
        val defn = defnO.get
        val modeDef = defn match {
          case DirectPartialCaseByCaseDefinien(cases, caseRes, defRes) => directPartialModeDefinitionInstance(name, argNum, argTps, defn.caseNum, cases, caseRes, defRes)
          case IndirectPartialCaseByCaseDefinien(cases, caseRes, defRes) => indirectPartialModeDefinitionInstance(name, argNum, argTps, defn.caseNum, cases, caseRes, defRes)
          case DirectCompleteCaseByCaseDefinien(cases, caseRes, completenessProof) => directCompleteModeDefinitionInstance(name, argNum, argTps, defn.caseNum, cases, caseRes)
          case IndirectCompleteCaseByCaseDefinien(cases, caseRes, completenessProof) => indirectCompleteModeDefinitionInstance(name, argNum, argTps, defn.caseNum, cases, caseRes)
        }
        List(modeDef)
    }
  }
  def translate_Private_Functor_Definition(private_Functor_Definition: Private_Functor_Definition)(implicit args: List[(Option[LocalName], objects.Term)]= Nil) = { ??? }
  def translate_Private_Predicate_Definition(private_Predicate_Definition: Private_Predicate_Definition)(implicit args: List[(Option[LocalName], objects.Term)]= Nil) = { ??? }
  def translate_Predicate_Definition(predicate_Definition: Predicate_Definition)(implicit args: List[(Option[LocalName], objects.Term)]= Nil) = { ??? }
}

object clusterTranslator {
  def translate_Cluster(cl:Cluster, cor_conds: List[Correctness_Condition] = Nil, args: List[(Option[LocalName], objects.Term)] = Nil): List[info.kwarc.mmt.api.symbols.Declaration] = {
    //TODO: Also translate the proofs of the correctness conditions
    cl._registrs map {
      case Conditional_Registration(pos, _attrs, _at, _tp) =>
        val tp = translate_Type(_tp)
        val adjs = attributeTranslator.translateAttributes(_attrs)
        val List(at) = attributeTranslator.translateAttributes(_at)
        val name = "existReg:"+pos.position
        conditionalRegistrationInstance(name, args map(_._2), tp, adjs, at)
      case Existential_Registration(pos, _adjClust, _tp) =>
        val tp = translate_Type(_tp)
        val adjs = attributeTranslator.translateAttributes(_adjClust)
        val name = "existReg:"+pos.position
        existentialRegistrationInstance(name, args map(_._2), tp, adjs)
      case Functorial_Registration(pos, _aggrTerm, _adjCl, _tp) =>
        val tm = translate_Term(_aggrTerm)
        val adjs = attributeTranslator.translateAttributes(_adjCl)
        val isQualified = _tp.isDefined
        val tp = _tp map translate_Type getOrElse({
          TranslationController.inferType(tm)})
        val name = "funcReg:"+pos.position
        if (isQualified) {
          qualFunctorRegistrationInstance(name, args map(_._2), tp, tm, adjs)
        } else {
          unqualFunctorRegistrationInstance(name, args map(_._2), tp, tm, adjs)
        }
      case Property_Registration(_props, _just) =>
        val Properties(Some(sort), None, Nil, Some(_tp)) = _props
        sort match {
          case "sethood" =>
            val just = justificationTranslator.translate_Justification(_just)
            val tp = translate_Type(_tp)
            val name = LocalName("sethood_of_"+tp.toStr(true))
            val tpO = Some(Apply(Mizar.constant("sethood"), tp))
            TranslationController.makeConstant(name, tpO, Some(just))
        }
    }
  }
}
object patternTranslator {
  def translate_Attribute_Pattern(atp:Attribute_Pattern):NotationContainer = {
    NotationContainer.empty()
  }
}

object blockTranslator {
  def translate_Definitional_Block(block:Block):List[symbols.Declaration] = {
    val definitionItems = block._items
    var args: List[(Option[LocalName], objects.Term)] = Nil
    var resDecls:List[symbols.Declaration] = Nil

    definitionItems foreach { it: Item =>
      it._subitem match {
        case loci_Declaration: Loci_Declaration =>
          args = args ++ subitemTranslator.translate_Loci_Declaration(loci_Declaration)
        case defn: Definition =>
          val translDef = definitionTranslator.translate_Definition(defn)(args)
          resDecls = resDecls ++ translDef
        case prop:Property => ???
        case defIt => throw DeclarationTranslationError("Definition expected inside definition-item.\n Instead found " + defIt.kind+". ", defIt)
      }
    }
    resDecls
  }
  def translate_Registration_Block(block: Block) : List[symbols.Declaration] = {
    val registrationItems = block._items
    var args: List[(Option[LocalName], objects.Term)] = Nil
    var resDecls:List[symbols.Declaration] = Nil

    registrationItems.zipWithIndex foreach { case (it: Item, ind: Int) =>
      it._subitem match {
        case loci_Declaration: Loci_Declaration =>
          args = args ++ subitemTranslator.translate_Loci_Declaration(loci_Declaration)
        case cl: Cluster =>
          val corr_conds = registrationItems.drop(ind).takeWhile({
            case it: Correctness_Condition => true
            case _ => false
          }) map {case corCond: Correctness_Condition => corCond}
          val translCluster = clusterTranslator.translate_Cluster(cl, corr_conds, args)
          resDecls = resDecls ++ translCluster
        case correctness_Condition: Correctness_Condition =>
        case otherIt =>
          throw DeclarationTranslationError("Cluster expected inside registration-item.\n Instead found " + otherIt.shortKind+". ", otherIt)
      }
    }
    resDecls
  }
  def translate_Justification_Block(block:Block) : Unit = {
    val justificationItems = block._items

    justificationItems foreach {
      case just:Justification =>
        val sourceReg = just.pos.sourceRegion()
        val translJust = justificationTranslator.translate_Justification(just)
        //TranslationController.addSourceRef(translJust, sourceReg)
        translJust
      case _ => throw new java.lang.Error("justification expected inside justification-item.")
    }
  }
}

sealed abstract class CaseByCaseDefinien {
  /**
   * Used to do inference on, mainly
   * @return
   */
  def someCase: objects.Term
  def cases: List[objects.Term]
  def caseRes: List[objects.Term]
  def caseNum = cases.length
}
case class DirectPartialCaseByCaseDefinien(cases: List[objects.Term], caseRes: List[objects.Term], defRes: objects.Term) extends CaseByCaseDefinien {
  override def someCase: objects.Term = defRes
}
object DirectPartialCaseByCaseDefinien {
  def apply(tm: objects.Term): DirectPartialCaseByCaseDefinien = DirectPartialCaseByCaseDefinien(Nil, Nil, tm)
}
case class IndirectPartialCaseByCaseDefinien(cases: List[objects.Term], caseRes: List[objects.Term], defRes: objects.Term) extends CaseByCaseDefinien {
  override def someCase: objects.Term = defRes
}
case class DirectCompleteCaseByCaseDefinien(cases: List[objects.Term], caseRes: List[objects.Term], completenessProof: Option[objects.Term] = None) extends CaseByCaseDefinien {
  override def someCase: objects.Term = caseRes.head
}
case class IndirectCompleteCaseByCaseDefinien(cases: List[objects.Term], caseRes: List[objects.Term], completenessProof: Option[objects.Term] = None) extends CaseByCaseDefinien {
  override def someCase: objects.Term = caseRes.head
}

object definiensTranslator {
  def translate_Definiens(defs:Definiens, just: Option[Justification] = None): CaseByCaseDefinien = {
    translate_CaseBasedExpr(defs._expr)
  }
  def translate_CaseBasedExpr(defn:CaseBasedExpr): CaseByCaseDefinien = {
    defn.check()
    if (defn.isSingleCase()) {
      DirectPartialCaseByCaseDefinien(translate_Expression(defn.singleCasedExpr._expr.get))
    } else {
      translate_Cased_Expression(defn.partialCasedExpr)
    }
  }
  def translate_Cased_Expression(partDef:PartialDef): CaseByCaseDefinien = {
    assert(partDef._partDefs.isDefined)
    partDef.check()
    val defRes = partDef._otherwise.get._expr map translate_Expression
    var isIndirect = false
    val complCases = partDef._partDefs.get._partDef map {
      case Partial_Definiens(_expr, _form) =>
        val caseCond = translate_Formula(_form)
        val caseRes = translate_Expression(_expr)
        if (caseRes.freeVars.contains(LocalName("it"))) {isIndirect = true}
        (caseCond, caseRes)
    }
    val (cases, caseRes) = complCases unzip
    val res : CaseByCaseDefinien = if (isIndirect) {
      if (defRes.isDefined) {
        IndirectPartialCaseByCaseDefinien(cases, caseRes, defRes.get)
      } else {
        IndirectCompleteCaseByCaseDefinien(cases, caseRes)
      }
    } else {
      if (defRes.isDefined) {
        DirectPartialCaseByCaseDefinien(cases, caseRes, defRes.get)
      } else {
        DirectCompleteCaseByCaseDefinien(cases, caseRes)
      }
    }
    res
  }

  object assumptionTranslator {
    def translateAssumption(ass:Assumption) = translateAssumptions(ass._ass)
      def translateAssumptions(ass:Assumptions) = { ??? }
  }
}

object registrationTranslator {
  def translateRegistration(reg:Registrations) = { ??? }
}
