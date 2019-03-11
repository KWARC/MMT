package info.kwarc.mmt.isabelle

import info.kwarc.mmt.api.ContentPath


object Ontology
{
  /* unary */

  def unary(subject: String, predicate: String): isabelle.RDF.Triple =
    isabelle.RDF.Triple(subject, predicate, Nil)

  def unary(subject: ContentPath, predicate: String): isabelle.RDF.Triple =
    unary(subject.toString, predicate)


  /* binary */

  def binary(subject: String, predicate: String, `object`: isabelle.XML.Body): isabelle.RDF.Triple =
    isabelle.RDF.Triple(subject, predicate, `object`)

  def binary(subject: String, predicate: String, `object`: String): isabelle.RDF.Triple =
    binary(subject, predicate, List(isabelle.XML.Text(`object`)))

  def binary(subject: ContentPath, predicate: String, `object`: isabelle.XML.Body): isabelle.RDF.Triple =
    binary(subject.toString, predicate, `object`)

  def binary(subject: ContentPath, predicate: String, `object`: String): isabelle.RDF.Triple =
    binary(subject.toString, predicate, `object`)

  def binary(subject: ContentPath, predicate: String, `object`: ContentPath): isabelle.RDF.Triple =
    binary(subject.toString, predicate, `object`.toString)


  /* namespace */

  val ulo: isabelle.XML.Namespace = isabelle.XML.Namespace("ulo", "ulo:")

  def rdf_document(triples: List[isabelle.RDF.Triple]): isabelle.XML.Elem =
    isabelle.RDF.document(isabelle.RDF.triples(triples),
      namespaces = isabelle.RDF.default_namespaces ::: List(ulo))

  object ULO
  {
    /* unaries */

    val `type` = ulo("type") // type constructors
    val data = ulo("data") // term constants

    val proposition = ulo("proposition")
    val statement = ulo("statement")  // command "axiomatization", "theorem", ..., "schematic_goal"

    val theory = ulo("theory") // theory, locale
    val instance = ulo("instance") // locale interpretation

    val section = ulo("section") // document headings: chapter, section, ..., subparagraph

    // unused: could be derived from sessions structure etc.
    val file = ulo("file")
    val folder = ulo("folder")
    val library = ulo("library")
    val group = ulo("group")

    val primitive = ulo("primitive") // consts or facts from "axiomatization"
    val derived = ulo("derived") // HOL typedefs, proven statements

    val theorem = ulo("theorem") // command "theorem" or "proposition"
    val lemma = ulo("lemma") // command "lemma" or "lemmas
    val corollary = ulo("corollary") // command "corollary"
    val conjecture = ulo("conjecture") // statement where proof contains "sorry"

    // unused: this is a property of the context, not of individual facts
    val simplification_rule = ulo("simplification-rule")

    // unused: could be result of "Judgement Day" Sledgehammer exploration
    val automatically_proved = ulo("automatically_proved")

    val experimental = ulo("experimental") // unused
    val deprecated = ulo("deprecated") // unused


    /* binaries */

    val declares = ulo("declares") // theory/locale declares item
    val uses = ulo("uses") // dependencies of term language (not proofs)

    // unused: somewhat biased towards Type Theory
    val type_depends_on_definition_of = ulo("type-depends-on-definition-of")
    val type_depends_on_type_of = ulo("type-depends-on-type-of")
    val definition_depends_on_type_of = ulo("definition-depends-on-type-of")
    val definition_depends_on_definition_of = ulo("definition-depends-on-definition-of")

    val generated_by = ulo("generated-by")
    val inductive_on = ulo("inductive-on")
    val mutual_with = ulo("mutual-with") // TODO e.g. Spec_Rules peer group


    // TODO manual markers!?
    val same_as = ulo("same-as")
    val similar_to = ulo("similar-to")
    val alternative_for = ulo("alternative-for")
    val see_also = ulo("see-also")
    val antonym_of = ulo("antonym-of")
    val formalizes = ulo("formalizes")
    val aligned_with = ulo("aligned_with")
    val inspired_by = ulo("inspired_by")

    val source_ref = ulo("source-ref") // entity position
    val check_time = ulo("check-time") // elapsed time (ms)
    val external_size = ulo("external-size") // source size (UTF-8 bytes)
    val internal_size = ulo("internal-size") // unused
  }
}