package info.kwarc.mmt.lf.structuralfeatures

import info.kwarc.mmt.api._
import objects._
import symbols._
import notations._
import checking._
import modules._
import frontend.Controller

import info.kwarc.mmt.lf._
import InternalDeclaration._
import InternalDeclarationUtil._

/** theories as a set of types of expressions */ 
class InductiveTypes extends StructuralFeature("inductive") with ParametricTheoryLike {

  /**
   * Checks the validity of the inductive type(s) to be constructed
   * @param dd the derived declaration from which the inductive type(s) are to be constructed
   */
  override def check(dd: DerivedDeclaration)(implicit env: ExtendedCheckingEnvironment) {
    //TODO: check for inhabitability
  }
  
  /**
   * Elaborates an declaration of one or multiple mutual inductive types into their declaration, 
   * as well as the corresponding no confusion and no junk axioms
   * Constructs a structure whose models are exactly the (not necessarily initial) models of the declared inductive types
   * @param parent The parent module of the declared inductive types
   * @param dd the derived declaration to be elaborated
   */
  def elaborate(parent: DeclaredModule, dd: DerivedDeclaration) = {
    val context = Type.getParameters(dd) 
    implicit val parentTerm = dd.path
    // to hold the result
    var elabDecls : List[Constant] = Nil
    implicit var tmdecls : List[TermLevel]= Nil
    implicit var statdecls : List[StatementLevel]= Nil
    implicit var tpdecls : List[TypeLevel]= Nil
    val decls = dd.getDeclarations map {
      case c: Constant =>
        val intDecl = InternalDeclaration.fromConstant(c, controller, Some(context))
        intDecl match {
          case d @ TermLevel(_, _, _, _, _,_) => tmdecls :+= d; intDecl
          case d @ TypeLevel(_, _, _, _,_) => tpdecls :+= d; intDecl
          case d @ StatementLevel(_, _, _, _,_) => statdecls :+= d; intDecl 
         }
      case _ => throw LocalError("unsupported declaration")
    }

    // copy all the declarations
    decls foreach {d => elabDecls ::= d.toConstant}
    
    // the no confusion axioms for the data constructors
    /*
     * For dependently-typed constructors, we cannot elaborate into plain LF:
     * some of the (in)equality axioms would be ill-typed because
     * the type system already forces elements of different instances of a dependent type to be unequal and of unequal type
     */
    elabDecls = elabDecls.reverse ::: tmdecls.flatMap(_.noConf(tmdecls)(dd.path))
    
    // the no junk axioms
    elabDecls ++= InternalDeclaration.noJunks(decls, context)(dd.path)
    
    elabDecls foreach {d =>
      //log(InternalDeclarationUtil.present(d))
      log(controller.presenter.asString(d))
    }
    new Elaboration {
      val elabs : List[Declaration] = Nil 
      def domain = elabDecls map {d => d.name}
      def getO(n: LocalName) = {
        elabDecls.find(_.name == n)
      }
    }
  }

}

object InductiveRule extends StructuralFeatureRule(classOf[InductiveTypes], "inductive")