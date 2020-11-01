/*
 *  IfElse.scala
 *  (ScalaCollider-If)
 *
 *  Copyright (c) 2016-2020 Hanns Holger Rutz
 *
 *	This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.synth
package ugen

import de.sciss.synth

/*

  // ---- Unit result ----

  If (freq > 100) Then {
    Out.ar(0, SinOsc.ar(freq))
  }

  If (freq > 100) Then {
    Out.ar(0, SinOsc.ar(freq))
  } Else {
    freq.poll(0, "freq")
  }

  // ---- GE result ----

  val res: GE = If (freq > 100) Then {
    SinOsc.ar(freq)
  } Else {
    WhiteNoise.ar
  }

  val res: GE = If (freq > 1000) Then {
    SinOsc.ar(freq)
  } ElseIf (freq > 100) Then {
    Dust.ar(freq)
  } Else {
    WhiteNoise.ar
  }


 */

final case class If(cond: GE) {
  def Then [A](branch: => A): IfThen[A] = {
    var res: A = null.asInstanceOf[A]
    val g = SynthGraph {
      res = branch
    }
    IfThen(cond, g, res)
  }
}

final case class IfLag(cond: GE, dur: GE) {
  def Then [A](branch: => A): IfLagThen[A] = {
    var res: A = null.asInstanceOf[A]
    val g = SynthGraph {
      res = branch
    }
    IfLagThen(cond = cond, dur = dur, branch = g, result = res)
  }
}

sealed trait Then[+A] extends Lazy {
  // this acts now as a fast unique reference
  @transient final protected lazy val ref = new AnyRef

  // ---- constructor ----
  SynthGraph.builder.addLazy(this)

  def cond  : GE
  def branch: SynthGraph
  def result: A

  private[synth] final def force(b: UGenGraph.Builder): Unit = UGenGraph.builder match {
    case nb: NestedUGenGraphBuilder =>
      visit(nb)
      ()
    case _ => sys.error(s"Cannot expand modular IfGE outside of NestedUGenGraphBuilder")
  }

  private[synth] final def visit(nb: NestedUGenGraphBuilder): NestedUGenGraphBuilder.ExpIfCase =
    nb.visit(ref, nb.expandIfCase(this))
}

sealed trait IfOrElseIfThen[+A] extends Then[A] {
  import ugen.{Else => _Else} // really, Scala?
  def Else [B >: A, Res](branch: => B)(implicit result: _Else.Result[B, Res]): Res = result.make(this, branch)
}

sealed trait IfThenLike[+A] extends IfOrElseIfThen[A] {
  def dur: GE

  def ElseIf (cond: GE): ElseIf[A] = new ElseIf(this, cond)
}

final case class IfThen[A](cond: GE, branch: SynthGraph, result: A)
  extends IfThenLike[A]
  with Lazy {

  def dur: GE = Constant.C0
}
final case class IfLagThen[A](cond: GE, dur: GE, branch: SynthGraph, result: A)
  extends IfThenLike[A]

final case class ElseIf[+A](pred: IfOrElseIfThen[A], cond: GE) {
  def Then [B >: A](branch: => B): ElseIfThen[B] = {
    var res: B = null.asInstanceOf[B]
    val g = SynthGraph {
      res = branch
    }
    ElseIfThen[B](pred, cond, g, res)
  }
}

sealed trait ElseOrElseIfThen[+A] extends Then[A] {
  def pred: IfOrElseIfThen[A]
}

final case class ElseIfThen[+A](pred: IfOrElseIfThen[A], cond: GE, branch: SynthGraph, result: A)
  extends IfOrElseIfThen[A] with ElseOrElseIfThen[A] {

  def ElseIf (cond: GE): ElseIf[A] = new ElseIf(this, cond)
}

object Else {
  object Result extends LowPri {
    implicit def GE: Else.GE.type = Else.GE
  }
  sealed trait Result[-A, Res] {
    def make(pred: IfOrElseIfThen[A], branch: => A): Res
  }

  object GE extends Result[synth.GE, ElseGE] {
    def make(pred: IfOrElseIfThen[GE], branch: => GE): ElseGE = {
      var res: GE = null
      val g = SynthGraph {
        res = branch
      }
      ElseGE(pred, g, res)
    }
  }

  final class Unit[A] extends Result[A, ElseUnit] {
    def make(pred: IfOrElseIfThen[A], branch: => A): ElseUnit =  {
      val g = SynthGraph {
        branch
      }
      ElseUnit(pred, g)
    }
  }

  trait LowPri {
    implicit final def Unit[A]: Unit[A] = instance.asInstanceOf[Unit[A]]
    private final val instance = new Unit[Any]
  }
}

sealed trait ElseLike[+A] extends ElseOrElseIfThen[A] {
  def cond: GE = Constant.C1
}

final case class ElseUnit(pred: IfOrElseIfThen[Any], branch: SynthGraph)
  extends ElseLike[Any] {

  def result: Any = ()
}

final case class ElseGE(pred: IfOrElseIfThen[GE], branch: SynthGraph, result: GE)
  extends ElseLike[GE] with GE /* .Lazy */ with AudioRated {

  private[synth] def expand: UGenInLike = {
    val b = UGenGraph.builder
    b.visit(ref, sys.error("Trying to expand ElseGE in same nesting level"))
  }
}

final case class ThisBranch() extends GE.Lazy with ControlRated {
  protected def makeUGens: UGenInLike =
    UGenGraph.builder match {
      case sysson: NestedUGenGraphBuilder => sysson.thisBranch
      case _ => sys.error("Cannot expand ThisBranch outside of NestedUGenGraphBuilder")
    }
}