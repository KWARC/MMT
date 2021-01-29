package info.kwarc.mmt.mizar.newxml.translator

import info.kwarc.mmt._
import api._
import info.kwarc.mmt.mizar.newxml.mmtwrapper.Mizar
import info.kwarc.mmt.mizar.newxml.translator.claimTranslator.translate_Claim
import objects.{Context, OMA, OMATTR, OMBINDC, OMFOREIGN, OMID, OML, OMLIT, OMLITTrait, OMSemiFormal, OMV, UnknownOMLIT}
import mizar.newxml.syntax._

object justificationTranslator {
  def translate_Justification(just:Justification, claim: objects.Term)(implicit defContext: DefinitionContext): Option[objects.Term] = just match {
    case Straightforward_Justification(pos, _refs) => None
    case Block(kind, pos, _items) =>
      //TODO: actually translate the proofs, may need additional arguments from the context, for instance the claim to be proven
      None
    case Scheme_Justification(pos, nr, idnr, schnr, spelling, _refs) => ???
  }
  def translate_Iterative_Equality_Proof(it: Iterative_Equality)(implicit defContext: DefinitionContext): objects.Term = { ??? }
  def translate_Proved_Claim(provedClaim: ProvedClaim)(implicit defContext: DefinitionContext) = {
    val claim = provedClaim._claim match {
      case Diffuse_Statement(spelling, serialnr, labelnr, _label) => provedClaim._just.get match {
        case Block(kind, pos, _items) =>
          val claims = _items.map(_._subitem match { case c: Claim => (true, Some(c)) case _ => (false, None) }).filter(_._1).map(_._2.get)
          Mizar.and(claims.map(translate_Claim(_)))
        case _ => Mizar.trueCon
      }
      case _ => translate_Claim(provedClaim._claim)
    }
    val prf = (provedClaim._claim, provedClaim._just) match {
      case (_, Some(just)) => translate_Justification(just, claim)
      case (it: Iterative_Equality, None) => Some(translate_Iterative_Equality_Proof(it))
      case (claim, None) => throw ProvedClaimTranslationError("No proof given for claim, which is not an iterative-equality (proving itself). ", provedClaim)
    }
    (claim, prf)
  }
}