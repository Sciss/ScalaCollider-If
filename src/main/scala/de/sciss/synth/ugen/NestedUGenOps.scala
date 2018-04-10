/*
 *  NestedUGenOps.scala
 *  (ScalaCollider-If)
 *
 *  Copyright (c) 2016 Institute of Electronic Music and Acoustics, Graz.
 *  Copyright (c) 2017-2018 Hanns Holger Rutz
 *
 *	This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.synth
package ugen

import de.sciss.synth.GraphFunction.Result

object NestedUGenOps {
  /** Wraps the body of the thunk argument in a `SynthGraph`, adds an output UGen, and plays the graph
    * on the default group of the default server.
    *
    * @param  thunk   the thunk which produces the UGens to play
    * @return         a reference to the node representing the spawned synths
    */
  def play[A: GraphFunction.Result](thunk: => A): Node = play()(thunk)

  /** Wraps the body of the thunk argument in a `SynthGraph`, adds an output UGen, and plays the graph
    * in a synth attached to a given target.
    *
    * @param  target      the target with respect to which to place the synth
    * @param  addAction   the relation between the new synth and the target
    * @param  outBus      audio bus index which is used for the synthetically generated `Out` UGen.
    * @param  fadeTime    if zero or positive, specifies the fade-in time for a synthetically added amplitude envelope.
    *                     If negative, no envelope will be used.
    * @param  thunk       the thunk which produces the UGens to play
    * @return             a reference to the node representing the spawned synths
    */
  def play[A](target: Node = Server.default, outBus: Int = 0,
                                    fadeTime: Double = 0.02,
                                    addAction: AddAction = addToHead)(thunk: => A)
             (implicit res: GraphFunction.Result[A]): Node = {
    val s = target.server
    val g = SynthGraph {
      val r = thunk
      res match {
        case Result.In(view) => WrapOut(view(r), fadeTime)
        case _ =>
      }
    }
    val res0: NestedUGenGraphBuilder.Result = NestedUGenGraphBuilder.build(g)
    val args          = List[ControlSet]("i_out" -> outBus, "out" -> outBus)
    val (bndl, node)  = NestedUGenGraphBuilder.prepare(res0, s, args)
    s ! bndl
    node
  }
}
