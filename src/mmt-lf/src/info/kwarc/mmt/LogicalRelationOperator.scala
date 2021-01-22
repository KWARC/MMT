package info.kwarc.mmt

import info.kwarc.mmt.api._
import info.kwarc.mmt.api.modules._
import info.kwarc.mmt.api.modules.diagrams.{Diagram, DiagramInterpreter, LinearTransformer, OperatorDSL, ParametricLinearOperator, Renamer, SimpleLinearModuleTransformer}
import info.kwarc.mmt.api.objects._
import info.kwarc.mmt.api.symbols.{Constant, Include, IncludeData, PlainInclude, Structure}
import info.kwarc.mmt.api.uom.{SimplificationUnit, Simplifier}
import info.kwarc.mmt.lf.LF

sealed case class LogicalRelationType(mors: List[Term], commonLinkDomain: MPath, commonLinkCodomain: MPath)
sealed case class LogicalRelationInfo(logrelType: LogicalRelationType, logrel: Term)

final class LogicalRelationTransformer(
                                        logrelType: MPath => LogicalRelationType,
                                        baseLogrelInfo: Option[LogicalRelationInfo] = None
                                      ) extends SimpleLinearModuleTransformer with OperatorDSL {

  override val operatorDomain: Diagram   =
    Diagram(List(LF.theoryPath) ::: baseLogrelInfo.map(_.logrelType.commonLinkDomain).toList)
  override val operatorCodomain: Diagram =
    Diagram(List(LF.theoryPath) ::: baseLogrelInfo.map(_.logrelType.commonLinkCodomain).toList)

  override def applyMetaModule(t: Term): Term = t match {
    case OMMOD(p) if baseLogrelInfo.exists(_.logrelType.commonLinkDomain == p) =>
      OMMOD(baseLogrelInfo.get.logrelType.commonLinkCodomain)

    case t => t
  }

  // todo: encode links in name?
  override protected def applyModuleName(name: LocalName): LocalName = name.suffixLastSimple("_logrel")

  override protected def beginTheory(thy: Theory, state: LinearState)(implicit interp: DiagramInterpreter): Option[Theory] = {
    val currentLinkDomain = logrelType(thy.path).commonLinkDomain
    if (!interp.ctrl.globalLookup.hasImplicit(thy.path, currentLinkDomain)) {
      interp.errorCont(InvalidElement(thy, s"Failed to apply logrel operator on `${thy.path}` because that theory " +
        s"is outside the domain of the morphisms, namely `$currentLinkDomain`."))
      return None
    }

    super.beginTheory(thy, state).map(outTheory => {
      val include = PlainInclude(logrelType(thy.path).commonLinkCodomain, outTheory.path)
      interp.add(include)
      interp.endAdd(include)

      outTheory
    })
  }

  override protected def beginView(view: View, state: LinearState)(implicit interp: DiagramInterpreter): Option[View] = {
    super.beginView(view, state).map(outView => {
      val currentLogrelCodomain = logrelType(view.to.toMPath).commonLinkCodomain
      val include = Include.assignment(
        outView.toTerm,
        currentLogrelCodomain,
        Some(OMIDENT(OMMOD(currentLogrelCodomain)))
      )
      interp.add(include)
      interp.endAdd(include)

      outView
    })
  }

  val logrel: Renamer[LinearState] = getRenamerFor("_r") // getRenamerFor("ʳ")

  override protected def applyConstantSimple(c: Constant, tp: Term, df: Option[Term])(implicit state: LinearState, interp: DiagramInterpreter): List[Constant] = {
    val lr = (p: GlobalName) => {
      if ((state.processedDeclarations ++ state.skippedDeclarations).exists(_.path == p)) {
        OMS(logrel(p))
      } else baseLogrelInfo match {
        case Some(LogicalRelationInfo(baseLogrelType, baseLogrel))
          if interp.ctrl.globalLookup.hasImplicit(p.module, baseLogrelType.commonLinkDomain) =>

          val t = interp.ctrl.globalLookup.ApplyMorphs(
            OMS(logrel.applyAlways(p)),
            baseLogrel
          )
          val s = t
          s

        case _ =>
          return NotApplicable(c, "refers to constant not previously seen. Implementation error?")
      }
    }

    val currentLogrelType = state.inContainer match {
      case thy: Theory => logrelType(thy.path)
      case link: Link => logrelType(link.to.toMPath)
    }

    val logicalRelation = new LogicalRelation(currentLogrelType.mors, lr, interp.ctrl.globalLookup)
    def g(t: Term): Term = betaReduce(Context.empty, logicalRelation.getExpected(Context.empty, c.toTerm, t), interp.ctrl.simplifier)

    // todo: also map definienses
    List(const(logrel(c.path), g(tp), None))
  }

  private def betaReduce(ctx: Context, t: Term, simplifier: Simplifier): Term = simplifier(
    t,
    SimplificationUnit(ctx, expandDefinitions = false, fullRecursion = true),
    RuleSet(lf.Beta)
  )
}

// todo: support complex morphisms
object LogicalRelationOperator extends ParametricLinearOperator {
  override val head: GlobalName = Path.parseS("http://cds.omdoc.org/urtheories?DiagramOperators?logrel_operator")

  override def instantiate(parameters: List[Term])(implicit interp: DiagramInterpreter): Option[LinearTransformer] = {
    parameters match {
      case OMMOD(domain) :: OMMOD(codomain) :: mors =>
        val constantLogrelType = (_: MPath) => LogicalRelationType(mors, domain, codomain)

        Some(new LogicalRelationTransformer(constantLogrelType))

      case _ =>
        None
    }
  }
}