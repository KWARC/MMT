package info.kwarc.mmt.api.utils
import scala.xml.Node

object xml {
   /** reads an XML file and returns the first Node in it */
   def readFile(file : java.io.File) : scala.xml.Node = {
      val src = scala.io.Source.fromFile(file, "utf-8") // utf-8 forced due to error with default codec
      val cp = scala.xml.parsing.ConstructingParser.fromSource(src, false)
      val N = cp.document()(0)
      src.close
      N
   }
  
   /** returns attribute value, "" if attribute not present */
   def attr(N : Node, att : String) : String = {
      val l = N.attribute(att).getOrElse(Nil).toList
      l.map(_.text).mkString("","","")
   }
   /** returns attribute value, default if attribute not present */
   def attr(N : Node, att : String, default : String) : String = {
      val l = N.attribute(att).map(_.toList)
      if (l.isEmpty)
         default
      else
         l.map(_.text).mkString("","","")
   }
   /**
    * Checks whether an XML element has illegal attribute keys.
    * Prefixed attributes are ignored.
    * @param md the attributes to be checked
    * @param the allowed keys
    * @return the illegal keys
    */
   def checkKeys(md : scala.xml.MetaData, keys : List[String]) : List[String] = md match {
      case scala.xml.Null => Nil
      case md : scala.xml.PrefixedAttribute => checkKeys(md.next, keys)
      case md : scala.xml.UnprefixedAttribute =>
         List(md.key).filterNot(keys.contains) ::: checkKeys(md.next, keys)
   }
   
   /** common namespaces */
   def namespace (ns : String) : String = {
      ns match {
         case "xml" => "http://www.w3.org/XML/1998/namespace"
         case "omdoc" => "http://www.mathweb.org/omdoc"
         case "jobad" => "http://omdoc.org/presentation"
         case "visib" => namespace("omdoc")
         case "om" => "http://www.openmath.org/OpenMath"
         case "xhtml" => "http://www.w3.org/1999/xhtml"
         case "html" => "http://www.w3.org/TR/REC-html40"
         case "mathml" => "http://www.w3.org/1998/Math/MathML"
      }
   }
}

case class URI(scheme: Option[String], authority: Option[String], path: List[String], absolute: Boolean, query: Option[String], fragment: Option[String]) {
   /** drop path, query, fragment, append (absolute) path of length 1 */
   def !/(n : String) : URI = this !/ List(n)
   /** drop path, query, fragment, append (absolute) path */
   def !/(p : List[String]) : URI = {
      URI(scheme, authority, p, true, None, None)
   }
   /** drop query, fragment, append one segment to path */   
   def /(n : String) : URI = this / List(n)
   /** drop query, fragment, append to path
    *  path stays relative/absolute; but URI(_, Some(_), Nil, false, _, _) / _ turns path absolute
    */   
   def /(p : List[String]) : URI = {
      val abs = absolute || (authority != None && path == Nil) 
      URI(scheme, authority, path ::: p, abs, None, None)
   }
   /** drops query and fragment, drop last path segment (if any) */
   def ^ : URI = URI(scheme, authority, if (path.isEmpty) Nil else path.init, absolute, None, None)
   /** drop query, fragment, append query */
   def ?(q: String) = URI(scheme, authority, path, absolute, Some(q), None)
   /** drop fragment, append fragment */
   def addFragment(f: String) = URI(scheme, authority, path, absolute, query, Some(f))
   
   /** parses a URI and resolves it against this */
   def resolve(s : String) : URI = resolve(URI(s))
   /** resolves a URI against this one (using the java.net.URI resolution algorithm except when u has no scheme, authority, path) */
   def resolve(u : URI) : URI = {
      //resolve implements old URI RFC, therefore special case for query-only URI needed
      if (u.scheme == None && u.authority == None && u.path == Nil)
         URI(scheme, authority, path, absolute, u.query, u.fragment)
      else
         URI(toJava.resolve(u.toJava))
   }
   /** returns the whole path as a string (/-separated, possibly with a leading /) */
   def pathAsString : String = path.mkString(if (absolute) "/" else "", "/", "")
   /*def resolve(u : java.net.URI) : URI = {
      //resolve implements old URI RFC, therefore special case for query-only URI needed
      if (u.getScheme == null && u.getAuthority == null && u.getPath == "")
         URI(scheme, authority, path, absolute, URI.nullToNone(u.getQuery), URI.nullToNone(u.getFragment))
      else
         URI(toJava.resolve(u))
   }*/
   def toJava = new java.net.URI(scheme.getOrElse(null), authority.getOrElse(null), pathAsString, query.getOrElse(null), fragment.getOrElse(null))
   override def toString = toJava.toString
}
   
object URI {
   private def nullToNone(s: String) = if (s == null) None else Some(s)
   /** transforms a Java URI into a URI */
   def apply(uri : java.net.URI) : URI = {
      val scheme = nullToNone(uri.getScheme)
      val authority = nullToNone(uri.getAuthority)
      val jpath = uri.getPath
      val (pathString, absolute) = {
         if (jpath.startsWith("/")) (jpath.substring(1), true)
         else (jpath, false)
      }
      var path = pathString.split("/",-1).toList
      if (path == List(""))  //note: split returns at least List(""), never Nil
         path = Nil
      val query = nullToNone(uri.getQuery)
      val fragment = nullToNone(uri.getFragment)
      URI(scheme, authority, path, absolute, query, fragment)
   }
   /** parses a URI (using the java.net.URI parser) */
   def apply(s : String) : URI = apply(new java.net.URI(s))
   /** returns a URI with scheme and authority only */
   def apply(s: String, a: String) : URI = URI(Some(s), Some(a), Nil, false, None, None)
   /** returns a URI with scheme, authority, absolute path, and query */
   def apply(scheme : String, authority : String, path : List[String], query : String) : URI =
      (URI(scheme, authority) / path) ? query
   /** returns a URI with a scheme only */
   def scheme(s: String) = URI(Some(s), None, Nil, false, None, None) 
   /** the URI "file:" */
   val file = scheme("file")
   /** returns a URI with no scheme or authority and relative path */
   def relative(path: String*) = URI(None, None, path.toList, false, None, None)
   implicit def toJava(u : URI) : java.net.URI = u.toJava
   implicit def fromJava(u : java.net.URI) = apply(u)
}



/*
//split key=value into (key,value), missing value defaults to ""
def splitKeyVal(p : String) : (String, String) = {
   val l = p.split("=",2)
   if (l.length == 1)
      (l(0), "") else (l(0),l(1))
}
def Unsplit(q : Map[String,String]) = q.elements.map(x => x._1 + "=" + x._2).toList.myFold("")((x,y) => x + ";" + y)
*/