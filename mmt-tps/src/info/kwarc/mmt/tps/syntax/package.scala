package info.kwarc.mmt.tps

/**
 * The classes in this package model the .omdoc generated by tps
 */
package object syntax {
   /**
    * @return a parser that takes PVS XML and returns classes in this package
    * 
    * The parser does not have to be changed if the classes in this package are changed.
    */
   def makeParser = new info.kwarc.mmt.api.utils.XMLToScala("info.kwarc.mmt.tps.syntax") {
      override def xmlName(s: String) = s.replace("$type", "type").replace("$for", "for")
      override def scalaName(s:String) = s.replace("type","$type").replace("for", "$for")
   }
}