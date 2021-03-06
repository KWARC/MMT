package info.kwarc.mmt.mizar.newxml.translator

import info.kwarc.mmt.api.documents.Document
import info.kwarc.mmt.api.utils.File
import info.kwarc.mmt.api.modules.Theory
import info.kwarc.mmt.api.notations.NotationContainer
import info.kwarc.mmt.api.objects.{OMMOD, OMV}
import info.kwarc.mmt.api.symbols.Declaration
import info.kwarc.mmt.api.{DPath, LocalName, NarrativeElement, archives, documents, objects}
import info.kwarc.mmt.lf.{Apply, ApplyGeneral}
import info.kwarc.mmt.mizar.newxml.Main.makeParser
import info.kwarc.mmt.mizar.newxml.mmtwrapper
import info.kwarc.mmt.mizar.newxml.syntax.Utils.MizarGlobalName
import info.kwarc.mmt.mizar.newxml.syntax._
import info.kwarc.mmt.mizar.newxml.translator._
import info.kwarc.mmt.mizar.newxml.translator.definiensTranslator.assumptionTranslator


object articleTranslator {
  def translateArticle(text_Proper: Text_Proper) = {
    val items = text_Proper._items map itemTranslator.translateItem
  }
}

import subitemTranslator._
object itemTranslator {
  // Adds the corresponding content to the TranslationController
  def translateItem(item: Item) = {
    val sourceReg = item.pos.sourceRegion()
    item.checkKind()
    val translatedSubitem : info.kwarc.mmt.api.ContentElement = item._subitem match {
      case subitem: MMLIdSubitem => subitem match {
        case scheme_Block_Item: Scheme_Block_Item => translate_Scheme_Block_Item(scheme_Block_Item)
        case theorem_Item: Theorem_Item => statementTranslator.translate_Theorem_Item(theorem_Item)
        case attrDef: Attribute_Definition => definitionTranslator.translate_Attribute_Definition(attrDef)
        case funcDef: Functor_Definition => definitionTranslator.translate_Functor_Definition(funcDef)
        case pd: Predicate_Definition => definitionTranslator.translate_Predicate_Definition(pd)
      }
      case existAss: Existential_Assumption => assumptionTranslator.translateAssumptions(existAss)
      case propReg: Property_Registration => registrationTranslator.translateRegistration(propReg:Registrations)
      case res: Reservation => translate_Reservation(res)
      case defIt: Definition_Item => translate_Definition_Item(defIt)
      case sectPragma: Section_Pragma => translate_Section_Pragma(sectPragma)
      case pr: Pragma => translate_Pragma(pr)
      case lociDecl: Loci_Declaration => translate_Loci_Declaration(lociDecl)
      case cl: Cluster => translate_Cluster(cl)
      case correctness: Correctness => translate_Correctness(correctness)
      case correctness_Condition: Correctness_Condition => translate_Correctness_Condition(correctness_Condition)
      case exemplification: Exemplification => translate_Exemplification(exemplification)
      case assumption: Assumption => translate_Assumption(assumption)
      case identify: Identify => translate_Identify(identify)
      case generalization: Generalization => translate_Generalization(generalization)
      case reduction: Reduction => translate_Reduction(reduction)
      case property: Property => translate_Property(property)
      case per_Cases: Per_Cases => translate_Per_Cases(per_Cases)
      case case_block: Case_Block => translate_Case_Block(case_block)
      case head: Heads => headTranslator.translate_Head(head)
      case nym: Nyms => nymTranslator.translate_Nym(nym)
      case st: Statement => statementTranslator.translate_Statement(st)
      case defn: Definition => definitionTranslator.translate_Definition(defn)
    }
    translatedSubitem match {
      case decl: Declaration => TranslationController.add(decl)
      case mod: info.kwarc.mmt.api.modules.Module => TranslationController.add(mod)
      case nar: NarrativeElement => TranslationController.add(nar)
    }
    //TranslationController.addSourceRef(translatedSubitem, sourceReg)
  }
}

import TranslationController._
class MizarXMLImporter extends archives.Importer {
  val key = "mizarxml-omdoc"
  def inExts = List("esx")

  def importDocument(bf: archives.BuildTask, index: documents.Document => Unit): archives.BuildResult = {
    val parser = makeParser
    val text_Proper = parser.apply(bf.inFile).asInstanceOf[Text_Proper]
    val doc = translate(text_Proper, bf)

    index(doc)
    //archives.BuildResult.empty
    archives.BuildResult.fromImportedDocument(doc)
  }

  def translate(text_Proper: Text_Proper, bf:archives.BuildTask) : Document = {
    val aid = text_Proper.articleid
    TranslationController.currentAid = aid
    TranslationController.currentOutputBase = bf.narrationDPath.^!

    val doc = TranslationController.makeDocument()
    //TranslationController.controller.build(TranslationController.currentBaseThyFile)(bf.errorCont)

    val thy = new Theory(currentThyBase, localPath, currentBaseThy, Theory.noParams, Theory.noBase)
    controller.add(thy)
    currentThy = thy
    //val th = TranslationController.makeTheory()

    articleTranslator.translateArticle(text_Proper)
    log("INDEXING ARTICLE: " + bf.narrationDPath.last)
    TranslationController.endMake()
    doc
  }
}