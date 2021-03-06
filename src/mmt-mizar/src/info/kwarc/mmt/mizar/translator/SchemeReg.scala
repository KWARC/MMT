package info.kwarc.mmt.mizar.translator

import info.kwarc.mmt.mizar.objects._
import info.kwarc.mmt.mizar.mmtwrappers._
import MizSeq._
import info.kwarc.mmt.api._
import info.kwarc.mmt.api.objects._
import info.kwarc.mmt.api.utils._
import info.kwarc.mmt.api.objects.Conversions._
import info.kwarc.mmt.lf._

object SchemeRegTranslator {

   def  translateRegistration(reg : MizRegistration) = {
     reg.cluster match {
         case rc : MizRCluster => translateRCluster(rc)
         case fc : MizFCluster => translateFCluster(fc)
         case cc : MizCCluster => translateCCluster(cc)
     }
   }

   def translateRCluster(rc : MizRCluster) = {
      val name = "RC" + rc.nr
      rc.args.zipWithIndex.map(p => TranslationController.addLocusVarBinder(Index(OMV("x"), OMI(p._2))))

      val argTypes = rc.args.map(TypeTranslator.translateTyp)

      val argNr = argTypes.length

      val typ = MMTUtils.PiArgs("x", argTypes.length, MMTUtils.PiArgTypes("x", argTypes, argTypes.length, TypeTranslator.translateTyp(rc.typ)))
      val cluster = MMTUtils.PiArgs("x", argTypes.length, MMTUtils.PiArgTypes("x", argTypes, argTypes.length, TypeTranslator.translateCluster(rc.cluster)))

      //val matches = (OMV("n") / OMI(argNr)) ++ (OMV("argTypes") / Sequence(argTypes :_*)) ++ (OMV("typ") / typ) ++ (OMV("cluster") / cluster)
      val matches = List(OMI(argNr), Sequence(argTypes :_*), typ, cluster)
      val pattern = RegPatterns.MizExistentialReg
      val i = MizInstance(OMMOD(TranslationController.currentTheory), LocalName(name), pattern.path, matches)
      TranslationController.clearLocusVarContext()
      TranslationController.addSourceRef(i, rc)
      TranslationController.add(i)
   }

   def translateFCluster(fc : MizFCluster) = {
      val name = "FC" + fc.nr
      fc.args.zipWithIndex.map(p => TranslationController.addLocusVarBinder(Index(OMV("x"), OMI(p._2))))

      val argTypes = fc.args.map(TypeTranslator.translateTyp)
      val argNr = argTypes.length
      val functor = MMTUtils.PiArgs("x", argTypes.length, MMTUtils.PiArgTypes("x", argTypes, argTypes.length, TypeTranslator.translateTerm(fc.functor)))
      val cluster = MMTUtils.PiArgs("x", argTypes.length, MMTUtils.PiArgTypes("x", argTypes, argTypes.length, TypeTranslator.translateCluster(fc.cluster)))

      //val matches = (OMV("n") / OMI(argNr)) ++ (OMV("argTypes") / Sequence(argTypes : _*)) ++ (OMV("functor") / functor) ++ (OMV("cluster") / cluster)
      val matches = List(OMI(argNr), Sequence(argTypes : _*), functor, cluster)
      val pattern = RegPatterns.MizFunctionalReg
      val i = MizInstance(OMMOD(TranslationController.currentTheory), LocalName(name), pattern.path, matches)

      TranslationController.clearLocusVarContext()

      TranslationController.addSourceRef(i, fc)
      TranslationController.add(i)

   }

   def translateCCluster(cc : MizCCluster) = {
      val name = "CC" + cc.nr

      cc.args.zipWithIndex.map(p => TranslationController.addLocusVarBinder(Index(OMV("x"), OMI(p._2))))

      val argTypes = cc.args.map(TypeTranslator.translateTyp)
      val argNr = argTypes.length
      val typ = MMTUtils.PiArgs("x", argTypes.length, MMTUtils.PiArgTypes("x", argTypes, argTypes.length, TypeTranslator.translateTyp(cc.typ)))
      val first = MMTUtils.PiArgs("x", argTypes.length, MMTUtils.PiArgTypes("x", argTypes, argTypes.length, TypeTranslator.translateCluster(cc.first)))
      val second = MMTUtils.PiArgs("x", argTypes.length, MMTUtils.PiArgTypes("x", argTypes, argTypes.length, TypeTranslator.translateCluster(cc.second)))

      //val matches = (OMV("n") / OMI(argNr)) ++ (OMV("argTypes") / Sequence(argTypes : _*)) ++ (OMV("typ") / typ) ++ (OMV("first") / first) ++ (OMV("second") / second)
      val matches = List(OMI(argNr),  Sequence(argTypes : _*), typ, first,  second)

      val pattern = RegPatterns.MizConditionalReg
      val i = MizInstance(OMMOD(TranslationController.currentTheory), LocalName(name), pattern.path, matches)
      TranslationController.clearLocusVarContext()
      TranslationController.addSourceRef(i, cc)
      TranslationController.add(i)
   }


   //Scheme

   def translateSchemeArg(a : MizSchemeArg) : Term = {
     a match {
       case f : MizSchemeFuncDecl =>
         val args = f.argTypes.map(TypeTranslator.translateTyp(_))
         val retType = TypeTranslator.translateTyp(f.retType)
         Arrow(args, retType)
       case p : MizSchemePredDecl =>
         val args = p.argTypes.map(TypeTranslator.translateTyp(_))
         val retType = Mizar.constant("prop")
         Arrow(args, retType)

     }
   }

   def translateScheme(s : MizSchemeDef) = {
     val name = "S" + s.schemeNr

     s.args.zipWithIndex.map(p => TranslationController.addLocusVarBinder(Index(OMV("x"), OMI(p._2))))


     val args = s.args.map(x => translateSchemeArg(x))

     val premises = s.premises.map(x => PropositionTranslator.translateProposition(x)).map(x => MMTUtils.PiArgs("x", args.length, MMTUtils.PiArgTypes("x", args, args.length, x)))

     val prop = MMTUtils.PiArgs("x", args.length, MMTUtils.PiArgTypes("x", args, args.length, PropositionTranslator.translateProposition(s.prop)))
     //pattern
     val inst = {
       val pattern = SchemePatterns.MizSchemeDef
       //val matches = ("n" / OMI(args.length)) ++ (OMV("args") / Sequence(args : _*)) ++ ("m" / OMI(premises.length)) ++ (OMV("premises") / Sequence(premises :_ *)) ++ ("prop" / prop)
       val matches = List(OMI(args.length), Sequence(args : _*), OMI(premises.length), Sequence(premises :_ *), prop)
       val i = MizInstance(OMMOD(TranslationController.currentTheory), LocalName(name), pattern.path, matches)
       TranslationController.clearLocusVarContext()
       TranslationController.addSourceRef(i, s)
       TranslationController.add(i)
     }
   }

}
