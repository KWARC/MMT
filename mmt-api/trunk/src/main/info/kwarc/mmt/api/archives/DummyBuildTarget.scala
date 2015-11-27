package info.kwarc.mmt.api.archives

import info.kwarc.mmt.api.utils.File

/** a simple build target for testing */
class DummyBuildTarget extends TraversingBuildTarget {
  def key = "foobar"

  def inDim = source

  def includeFile(n: String) = n.endsWith(".foo")

  def outDim = RedirectableDimension(key)

  override val outExt = "bar"

  def buildFile(bf: BuildTask): BuildResult = {
    var missingDeps: List[ResourceDependency] = Nil
    var providedEntities: List[String] = Nil
    var readDeps = true
    val source = scala.io.Source.fromFile(bf.inFile)
    source.getLines().foreach { line =>
      val l = line.trim
      if (l == "#provides") {
        readDeps = false
      } else if (readDeps) {
        val d = File(l)
        if (!d.exists) {
          missingDeps ::= PhysicalDependency(d)
        }
      } else {
        providedEntities ::= l
      }
    }
    if (missingDeps.isEmpty)
      if (providedEntities.isEmpty) BuildFailure(Nil, Nil)
      else
        BuildSuccess(Nil, Nil)
    else MissingDependency(missingDeps, Nil)
  }
}