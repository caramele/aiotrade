package org.aiotrade.lib.util.actors

import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer

object Reactions {
  import scala.ref._

  
  final class Impl extends Reactions {
    private val parts: Buffer[Reaction] = new ListBuffer[Reaction]
    def isDefinedAt(e: Any) = parts.exists(_ isDefinedAt e)
    def += (r: Reaction): this.type = { parts += r; this }
    def -= (r: Reaction): this.type = { parts -= r; this }
    def apply(e: Any) {
      for (p <- parts if p isDefinedAt e) p(e)
    }
  }

  final type Reaction = PartialFunction[Any, Unit]

  /**
   * A Reaction implementing this trait is strongly referenced in the reaction list
   */
  trait StronglyReferenced

  final class Wrapper(listener: Any)(r: Reaction) extends Reaction with StronglyReferenced with Proxy {
    def self = listener
    def isDefinedAt(e: Any) = r.isDefinedAt(e)
    def apply(e: Any) { r(e) }
  }
}

/**
 * Used by reactors to let clients register custom event reactions.
 */
abstract class Reactions extends Reactions.Reaction {
  /**
   * Add a reaction.
   */
  def += (r: Reactions.Reaction): this.type

  /**
   * Remove the given reaction.
   */
  def -= (r: Reactions.Reaction): this.type
}