package info.kwarc.mmt.moduleexpressions.operators

import info.kwarc.mmt.api.objects.{AnonymousTheory, Context, OMID, OML, Term, Traverser}
import info.kwarc.mmt.api.uom._
import info.kwarc.mmt.api.utils.URI
import info.kwarc.mmt.api.{DPath, GlobalName, LocalName}
import info.kwarc.mmt.lf.{ApplySpine, FunTerm, FunType}

import scala.collection.mutable

private object TypeOperator extends TheoryScala {
  val _base = DPath(URI("https://example.com/diagops"))
  val _name = LocalName("TypeOperator")

  val typeOp: GlobalName = _path ? "typeOp"
}

object TypeIndexifier extends UnaryConstantScala(Combinators._path, "typeindexifier") {
  /** the label of the distinguished node of the output diagram */
  val nodeLabel = LocalName("pres")
}

// Store which typeindexed sorts the declarations in our inputTheory (transitively) depend on
// E.g. if we have `op: tm a`, then we would have `op |-> Set(a)`
// E.g. if we have additionally `op2: tm b -> tm a ❘ = op`, then we would have `op |-> Set(a, b)`
final class ComputeTypeIndexedHelperContext(val sortDependencies: mutable.HashMap[LocalName, List[LocalName]], val sortDependenciesSeeker: SortDependenciesSeeker) {
}

object ComputeTypeIndexed extends FunctorialDiagramOperatorComputationRule[ComputeTypeIndexedHelperContext](TypeIndexifier) {
  override val unaryConstant: UnaryConstantScala = TypeIndexifier

  override protected def initHelperContext: ComputeTypeIndexedHelperContext
    = new ComputeTypeIndexedHelperContext(mutable.HashMap(), new SortDependenciesSeeker)

  override def applicableOnTheory(thy: AnonymousTheory): Boolean = {
    // TODO: Check that the meta-theory contains SFOL (ex. of implicit morphism, I guess) */
    true
  }

  override def transformSingleDeclaration(decl: OML, context: Context, helperContext: ComputeTypeIndexedHelperContext)
  : OperatorResult[(OML, ComputeTypeIndexedHelperContext)] = decl.tp match {
    case Some(SFOL.FunctionOrPredicateType(_)) =>
      helperContext.sortDependencies.put(
        decl.name,
        helperContext.sortDependenciesSeeker.getDependenciesForDecl(decl, helperContext.sortDependencies)
      )
      val newDecl = TypeIndexer.typeIndex(decl, helperContext.sortDependencies)

      // TODO: Adjust indices in notation container

      TransformedResult((newDecl, helperContext))

    // vvv Remainder to potentially account for this in the future
    case Some(PL.ded(_)) => NotApplicable()
    case _ => NotApplicable()
  }
}

private object TypeIndexer {
  def typeIndex(decl: OML, allDependencies: collection.Map[LocalName, collection.Seq[LocalName]]): OML = {
    assert(allDependencies.contains(decl.name))

    val adder = new DependenciesAndTypeOperatorAdder

    val midDecl = adder.traverse(decl)(Context.empty, allDependencies).asInstanceOf[OML] // return value is indeed an OML by recursion

    midDecl.copy(
      tp = midDecl.tp.map(dependentlyTypeTypeComponent(_, allDependencies(decl.name))),
      df = midDecl.df.map(lambdaBindDefComponent(_, allDependencies(decl.name)))
    )
  }

  def dependentlyTypeTypeComponent(typeComponent: Term, dependenciesToAbstract: Seq[LocalName]): Term = {
    val dependentlyBoundVariables = dependenciesToAbstract.map(sortName =>
      (Some(sortName), OMID(TypedTerms.typeOfSorts))
    ).toList

    FunType(dependentlyBoundVariables, typeComponent)
  }

  def lambdaBindDefComponent(defComponent: Term, dependenciesToBind: Seq[LocalName]): Term = {
    val variablesToBind = dependenciesToBind.map(sortName =>
      (sortName, OMID(TypedTerms.typeOfSorts))
    ).toList

    FunTerm(variablesToBind, defComponent)
  }

  final private class DependenciesAndTypeOperatorAdder extends Traverser[collection.Map[LocalName, collection.Seq[LocalName]]] {
    def traverse(t: Term)(implicit con: Context, state: State): Term = t match {
      // We reference another decl, which is *not* shadowed by some binder
      case ApplySpine(referencedDecl@OML(_, _, _, _, _), args) if state.contains(referencedDecl.name) =>
        val sortDependencies = state.getOrElse(referencedDecl.name, Nil)

        val newArgs = sortDependencies.map(sortName => OML(sortName, None, None, None, None)).toList ::: args
        ApplySpine(referencedDecl, newArgs: _*)

      case ApplySpine(OMID(TypedTerms.termsOfSort), sort) =>
        ApplySpine(OMID(TypedTerms.termsOfSort), ApplySpine(OMID(TypeOperator.typeOp), sort: _*))

      case t => Traverser(this, t)
    }
  }

}

final class SortDependenciesSeeker extends Traverser[(collection.Map[LocalName, collection.Seq[LocalName]], mutable.HashSet[LocalName])] {

  def getDependenciesForDecl(decl: OML, currentDependencies: collection.Map[LocalName, collection.Seq[LocalName]]): List[LocalName] = {
    implicit val context: Context = Context.empty
    implicit val state: State = (currentDependencies, mutable.HashSet[LocalName]())

    // Beware to not call traverse on decl itself
    // Since it then would confuse the outer OML with an OML reference to another declaration
    // (The underlying "problem" is that OMLs can function both as declarations as well as references to declarations.)
    decl.tp.map(traverse)
    decl.df.map(traverse)

    // TODO: Enforce some arbitrary order, fix alphabetical order sometime
    state._2.toList
  }

  def traverse(t: Term)(implicit con: Context, state: State): Term = t match {
    // We reference another decl, which is *not* shadowed by some binder
    case OML(referencedDecl, _, _, _, _) if !con.exists(_.name == referencedDecl) =>
      state._2 ++= state._1.getOrElse(referencedDecl, Set())
      null
    case ApplySpine(OMID(TypedTerms.termsOfSort), List(OML(referencedSort, _, _, _, _))) =>
      state._2 += referencedSort
      null
    case t => Traverser(this, t)
  }
}