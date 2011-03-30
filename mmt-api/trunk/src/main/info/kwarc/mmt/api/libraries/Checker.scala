package info.kwarc.mmt.api.libraries
import info.kwarc.mmt.api._
import info.kwarc.mmt.api.modules._
import info.kwarc.mmt.api.symbols._
import info.kwarc.mmt.api.objects._
import info.kwarc.mmt.api.patterns._
import info.kwarc.mmt.api.ontology._
import info.kwarc.mmt.api.utils._

/** CheckResult is the result type of checking a content element */
sealed abstract class CheckResult
/** the content element is well-formed
 * @param decls the list of dependencies of the object 
 */
case class Success(deps : List[ABoxDecl]) extends CheckResult
/** the content element is well-formed and was reconstructed to several new objects (e.g., type inference)
 * @param recon the reconstructed content elements
 */
case class Reconstructed(recon : List[ContentElement], deps : List[ABoxDecl]) extends CheckResult
/** the content element is ill-formed
 * @param msg an error message 
 */
case class Fail(msg : String) extends CheckResult

abstract class Checker {
   protected def lookupTheory(lib : Lookup, t : MPath) {
      lib.getTheory(t) // can throw NotFound(t)
   }
   /** checks whether a theory object is well-formed relative to a library
    *  @param lib the library
    *  @param t the theory
    *  @return the list of identifiers occurring in t
    */
   def checkTheo(t : TheoryObj)(implicit lib : Lookup) : List[Path] = t match {
     case OMMOD(p) =>
       lookupTheory(lib, p)
       List(p)
   }
   protected def checkLink(l : Link, path : Path)(implicit lib : Lookup) : List[ABoxDecl] = {
	  val todep = l match {
         case _ : View =>
            val ds = checkTheo(l.to).map(HasOccurrenceOfInCodomain(path,_))
            IsView(path) :: ds
         case _ : Structure => List(IsStructure(path))
      }
      val fromdep = checkTheo(l.from).map(HasOccurrenceOfInDomain(path,_))
      todep ::: fromdep
   }
   
   def check(s : ContentElement)(implicit lib : Lookup) : CheckResult
}

/**
 * A Checker that always succeeds.
 */
object NullChecker extends Checker {
   def check(s : ContentElement)(implicit lib : Lookup) = Success(Nil)
}

/**
 * A Checker that makes sure all referenced theories are loaded and infers simple ABox elements,
 * but checks nothing else.
 */
object GraphChecker extends Checker {
   def check(s : ContentElement)(implicit lib : Lookup) = {
      s match {
	      case PlainInclude(t, f) =>
	         lookupTheory(lib, f)
             Success(List(HasOccurrenceOfInImport(t, f)))
	      case t : DeclaredTheory =>
	         val mdep = t.meta match {
	            case Some(m) => lookupTheory(lib, m); List(HasMeta(t.path, m))
                case _ => Nil
	         }
             Success(IsTheory(t.path) :: mdep)
	      case l : Link =>
             val tpdep = l match {
               case _ : View => IsView(l.path)
               case _ : Structure => IsStructure(l.path)
             }
             Success(checkLink(l, s.path))
          //TODO: should also load all views
          case _ => Success(Nil)
      }
   }
}

/**
 * A Checker that implements MMT-wellformedness relative to a foundation.
 */
class FoundChecker(foundation : Foundation) extends Checker {
   /** checks whether a content element may be added to a library
    * @param lib the library
    * @param s the content element
    * throws backend.NotFound(p) if possibly well-formed after retrieving p
    */
   def check(s : ContentElement)(implicit lib : Lookup) : CheckResult = try {
      s match {
         case s: Declaration => s.home match {
            case OMMOD(p) => p
            case OMDL(OMMOD(p), !(n)) => 
            case _ => throw Invalid("adding declarations to composed theories not allowed")
         }
      }
      /*s match {
         case t : DeclaredTheory =>
            if (! t.isEmpty) return Fail("new theory not empty")
            val d = List(IsTheory(t.path))
            t.meta match {
               case Some(mt) =>
                  lookupTheory(lib, mt)
                  Reconstructed(List(t, PlainInclude(mt, t.path)), HasMeta(t.path, mt) :: d)
               case _ =>
                  Success(d)
            }
            
         case t : DefinedTheory =>
            val occs = checkTheo(t.df)
            val deps = occs.map(HasOccurrenceOfInDefinition(t.path, _))
            Success(IsTheory(t.path) :: deps)
         case l : DeclaredLink =>
            if (! l.isEmpty) return Fail("new declared link not empty")
            Success(checkLink(l, s.path))
         case l : DefinedLink =>
            val (occs, from, to) = inferMorphism(l.df)
            if (! lib.imports(l.from, from))
               return Fail("definition of defined link does not type-check: " + l.from + " is not imported into " + from)
            if (! lib.imports(to, l.to))
               return Fail("definition of defined link does not type-check: " + to + " is not imported into " + l.to)
            val deps = occs.map(HasOccurrenceOfInDefinition(l.path, _))
            Success(checkLink(l, s.path) ::: deps)
         case l: IncludeLink => Success(Nil) //TODO
/*         case TheoImport(par, of) =>
            lookupTheory(lib, par)
            val occs = checkTheo(of)
            val deps = occs.map(HasOccurrenceOfInImport(par, _))
            Success(deps) */
         case c : Constant =>
            val parent : MPath = c.parent.asPath.getOrElse(throw Invalid("adding constants to composed theories not allowed"))
            val occtp = if (c.tp.isDefined) {
               checkTerm(parent, c.tp.get, c.uv)
            } else
               Nil
            val occdf = if (c.df.isDefined) checkTerm(parent, c.df.get, c.uv) else Nil
            if (! foundation.typing(c.df, c.tp)) return Fail("definition of constant does not type-check")
            val deps = IsConstant(c.path) ::  
              occtp.map(HasOccurrenceOfInType(c.path, _)) ::: occdf.map(HasOccurrenceOfInDefinition(c.path, _))
            Success(deps)
         case a : Alias =>
            lib.get(a.forpath)
            if (lib.imports(a.forpath.parent, a.parent))
               Success(List(IsAlias(a.path), IsAliasFor(a.path, a.forpath)))
            else
               Fail("illegal alias")
         case a : ConstantAssignment =>
            val link = lib.getLink(a.parent)
            val sourceSym = try {lib.getSymbolNoAlias(link.from ? a.name)}
                         catch {case _ => return Fail("assignment to non-existing constant")}
            val source = sourceSym match {
                case s : Structure => return Fail("assignment to structure")
                case c : Constant => if (c.parent == link.from) c else return Fail("assignment to included constant")
            }
            val defleq = source.df.isEmpty || a.target == OMHID() || foundation.equality(source.df.get, a.target)
            if (! defleq) return Fail("assignment violates definedness ordering")
            val occas = checkTerm(link.to, a.target, source.uv)
            if (! foundation.typing(Some(a.target), source.tp.map(_ * a.parent)))
               return Fail("assignment does not type-check")
            val deps = IsConAss(a.path) :: occas.map(HasOccurrenceOfInTarget(a.path, _))
            Success(deps)
         case a : StructureAssignment =>
            val link = lib.getLink(a.parent)
            val source = try {lib.getStructure(link.from ? a.name)}
                         catch {case _ => return Fail("assignment to non-existing structure")}
            val occs = checkMorphism(a.target, source.from, link.to)
            //TODO: checking of equality
            val deps = IsConAss(a.path) :: occs.map(HasOccurrenceOfInTarget(a.path, _))
            Success(deps)
         case a: IncludeAssignment => Success(Nil) //TODO
/*         case LinkImport(par, of) =>
            lib.getLink(par)
            val (occs, _, _) = inferMorphism(of)
            val deps = occs.map(p => HasOccurrenceOfInImport(par, p))
            Success(deps)*/
            //TODO: check for overlap between imported morphisms
         case a : Open =>
            val str = lib.getStructure(a.parent)
            val source = try {lib.getSymbol(str.from ? a.name)}
                         catch {case _ => return Fail("open of non-existing constant")}
            val name = a.as.map(LocalName(_)).getOrElse(a.name)
            val al = new Alias(str.to, name, str.to ? str.name / source.name)
            Reconstructed(List(a, al), List(IsOpen(a.path)))
         case p : Pattern => Success(Nil) // TODO
         case i : Instance => Reconstructed(List(Pattern.elaborate(i,lib)),Nil)
      } */
      Success(Nil)
   } catch {
      case Invalid(msg) => Fail("content element contains ill-formed object: " + msg)
      case GetError(msg) => Fail("content element contains ill-formed object: get error: " + msg)
   }
   
   /**
    * Checks structural well-formedness of a closed term relative to a library and a home theory.
    * @param lib the library
    * @param home the home theory
    * @param s the term
    * @param univ the universe to check the term against
    * @return the list of identifiers occurring in s (no duplicates, random order)
    */
   def checkTerm(home : MPath, s : Term, univ : Universe)(implicit lib : Lookup) : List[Path] = {
      lookupTheory(lib, home)
      checkTerm(OMMOD(home), Context(), s, IsEqualTo(univ)).distinct
   }
   //TODO redesign role checking, currently not done
   private def checkTerm(home : TheoryObj, context : Context, s : Term, uvcheck : UnivCheck)(implicit lib : Lookup) : List[Path] = {
      s match {
         case OMID((h: TheoryObj) % (IncludeStep(from) / ln)) =>
            if (! lib.imports(from, h))
               throw Invalid(from + " is not imported into " + h + " in " + s)
            checkTerm(home, context, OMID(from % ln), uvcheck)
         case OMID(path) =>
            //val toccs = checkTheo(path.parent) TODO check theory?
            val s = try {lib.get(path)}
                    catch {case GetError(msg) =>
                                throw Invalid("ill-formed constant reference: " + msg)
                    }
            s match {
               case a : Alias =>
                  if (! lib.imports(a.home, home))
                     throw Invalid("constant " + s + " is not imported into home theory " + home)
                  List(path)
               case c : Constant =>
                  if (! lib.imports(c.home, home))
                     throw Invalid("constant " + s + " is not imported into home theory " + home)
/*                  uvcheck(c.uv) match {
                     case None => ()
                     case Some(msg) => //throw Invalid(msg)
} */
                  List(path)
               case _ => throw Invalid(path + " does not refer to constant")
            }
         //for now: variables check against any role
         case OMV(name) =>
            if (! context.isDeclared(name)) throw Invalid("variable is not declared: " + name)
            Nil
         case OMA(fun, args) =>
            val occf = checkTerm(home, context, fun, IsSemantic) //TODO level of application cannot be inferred
            val occa = args.flatMap(checkTerm(home, context, _, IsSemantic))
            occf ::: occa
         case OMBINDC(binder, bound, condition, scope) =>
            val newcontext = context ++ bound
            val occb = checkTerm(home, context, binder, IsEqualTo(Binder))
            val occv = bound.variables.flatMap {
               // not checking the attributions
               case TermVarDecl(_, tp, df, attrs @ _*) => List(tp,df).filter(_.isDefined).map(_.get).flatMap(
                     checkTerm(home, newcontext, _, IsSemantic)
               )
            }
            val occc = if (condition.isDefined) checkTerm(home, newcontext, condition.get, IsSemantic)
               else Nil
            val occs = checkTerm(home, newcontext, scope, IsSemantic)
            occb ::: occv.toList ::: occc ::: occs
         case OMATTR(arg, key, value) =>
            val occa = checkTerm(home, context, arg, uvcheck)
            val occk = checkTerm(home, context, key, IsEqualTo(Key))
            val occv = checkTerm(home, context, value, IsSemantic)
            occa ::: occk ::: occv
         case OMM(arg, morph) =>
            val (occm, from, to) = inferMorphism(morph)
            if (! lib.imports(to, home))
               throw Invalid("codomain of morphism is not imported into expected home theory")
            val occa = checkTerm(from, context, arg, uvcheck) // using the same context because variable attributions are ignored anyway
            occm ::: occa
         case OMHID => Nil//TODO roles
         case OME(err, args) =>
            val occe = checkTerm(home, context, err, IsEqualTo(Error))
            val occa = args.flatMap(checkTerm(home, context, _, IsSemantic))
            occe ::: occa
         case OMFOREIGN(node) => Nil //TODO roles, dependencies?
         case OMI(i) => Nil //TODO roles; check import of pseudo-theories, dependencies?
         case OMSTR(s) => Nil //TODO roles; check import of pseudo-theories, dependencies?
         case OMF(d) => Nil //TODO roles; check import of pseudo-theories, dependencies?
      }
   }
   
   /** checks whether a morphism object is well-formed relative to a library and infers its type
    *  @param lib the library
    *  @param m the theory
    *  @return the list of named objects occurring in m, the domain and codomain of m
    */
   def inferMorphism(m : Morph)(implicit lib : Lookup) : (List[Path], TheoryObj, TheoryObj) = m match {
     case OMIDENT(t) =>
        val occs = checkTheo(t)
        (occs, t, t)
     case OMMOD(m : MPath) =>
        val l = lib.getLink(m)
        (List(m), l.from, l.to)
     case OMDL(to, name) =>
        val occs = checkTheo(to)
        val from = lib.get(to % name) match {
           case l: DefinitionalLink => l.from
           case _ => throw Invalid("invalid morphism " + m)
        }
        (occs, from, to)
     case OMCOMP(Nil) => throw Invalid("cannot infer type of empty composition")
     case OMCOMP(hd :: Nil) => inferMorphism(hd)
     case OMCOMP(hd :: tl) =>
        val (l1, r,s1) = inferMorphism(hd)
        val (l2, s2,t) = inferMorphism(OMCOMP(tl))
        if (lib.imports(s1,s2))
           (l1 ::: l2, r, t)
        else
           throw Invalid("ill-formed morphism: " + hd + " cannot be composed with " + tl)
     case OMEMPTY(f,t) =>
        val occs = checkTheo(f) ::: checkTheo(t)
        (occs, f,t)
   }
   /** checks whether a morphism object is well-formed relative to a library, a domain and a codomain
    *  @param lib the library
    *  @param m the morphism
    *  @param dom the domain
    *  @param cod the codomain
    *  @return the list of identifiers occurring in m
    */
   def checkMorphism(m : Morph, dom : TheoryObj, cod : TheoryObj)(implicit lib : Lookup) : List[Path] = {
      val (l, d,c) = inferMorphism(m)
      if (! lib.imports(dom, d) || ! lib.imports(c, cod))
         throw Invalid("ill-formed morphism: expected " + dom + " -> " + cod + ", found " + c + " -> " + d)
      l
   }
}
