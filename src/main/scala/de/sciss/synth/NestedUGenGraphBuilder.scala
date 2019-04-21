/*
 *  NestedUGenGraphBuilder.scala
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

import de.sciss.synth.Ops.stringToControl
import de.sciss.synth.impl.BasicUGenGraphBuilder
import de.sciss.synth.ugen.impl.BranchOut
import de.sciss.synth.ugen.{BinaryOpUGen, Constant, ControlProxyLike, Done, ElseGE, ElseOrElseIfThen, IfThenLike, In, OneZero, Out, Schmidt, SetResetFF, Then, ToggleFF, Trig1, UnaryOpUGen}
import de.sciss.{osc, synth}

import scala.annotation.{elidable, tailrec}
import scala.collection.immutable.{IndexedSeq => Vec, Set => ISet}

object NestedUGenGraphBuilder {
  /** Takes a builder result and prepares playback by constructing
    * an OSC bundle with all the required synth-defs, groups, synths,
    * and controls.
    *
    * @param res0   the top result to play
    * @param s      the server on which to play
    * @param args   additional synth control args
    *
    * @return a tuple formed of the OSC bundle ready to be sent, and the main
    *         node which can be used for further control, `free()` etc.
    */
  def prepare(res0: Result, s: Server, args: List[ControlSet] = Nil): (osc.Bundle, synth.Node) = {
    var defs  = List.empty[SynthDef]
    var defSz = 0   // used to create unique def names
    var msgs  = List.empty[osc.Message] // synchronous
    var ctl   = args.reverse  // init
    var buses = List.empty[Bus]

    def loop(child: NestedUGenGraphBuilder.Result, parent: Group, addAction: AddAction): Node = {
      val name        = s"test-$defSz"
      val sd          = SynthDef(name, child.graph)
      defs          ::= sd
      defSz          += 1
      val syn         = Synth(s)
      val hasChildren = child.children.nonEmpty
      val group       = if (!hasChildren) parent else {
        val g   = Group(s)
        msgs  ::= g.newMsg(parent, addAction)
        g
      }
      val node  = if (hasChildren) group else syn
      msgs ::= syn.newMsg(name, target = group, addAction = if (hasChildren) addToHead else addAction)

      child.children.foreach { cc =>
        val ccn = loop(cc, group, addToTail)
        if (cc.id >= 0) ctl ::= NestedUGenGraphBuilder.pauseNodeCtlName(cc.id) -> ccn.id
      }

      child.links.foreach { link =>
        val bus = link.rate match {
          case `audio`    => Bus.audio  (s, numChannels = link.numChannels)
          case `control`  => Bus.control(s, numChannels = link.numChannels)
          case other      => throw new IllegalArgumentException(s"Unsupported link rate $other")
        }
        buses ::= bus
        ctl   ::= NestedUGenGraphBuilder.linkCtlName(link.id) -> bus.index
      }

      node
    }

    val mainNode = loop(res0, parent = s.defaultGroup, addAction = addToHead)
    mainNode.onEnd {
      buses.foreach(_.free())
    }

    msgs ::= mainNode.setMsg(ctl.reverse: _*)
    msgs ::= synth.message.SynthDefFree(defs.map(_.name): _*)

    val b1 = osc.Bundle.now(msgs.reverse: _*)
    val defL :: defI = defs
    val async = defL.recvMsg(b1) :: defI.map(_.recvMsg)
    val b2 = osc.Bundle.now(async.reverse: _*)

    (b2, mainNode)
  }

  /** Links denote buses between parent and child nodes.
    *
    * @param id           the identifier which will be used by the
    *                     source and sink through the control named
    *                     `linkCtlName`
    * @param rate         the bus rate (`control` or `audio`)
    * @param numChannels  the number of channels of the bus
    */
  final case class Link(id: Int, rate: Rate, numChannels: Int) {
    require(rate == control || rate == audio, s"Unsupported link rate $rate")
  }

  /** Structural data for the 'top' of an if-then-else block, i.e.
    * the list of branches, the accumulated conditional signal,
    * the lag time (if any), the identifier of the result bus,
    * and the number of channels (after the children have been expanded).
    */
  private[synth] final class ExpIfTop(val lagTime: GE, val selBranchId: Int) {
    val hasLag      : Boolean         = lagTime != Constant.C0
    var branchCount : Int             = 0
    var branches    : List[ExpIfCase] = Nil
    var condAcc     : GE              = Constant.C0

    var resultLinkId: Int             = -1  // summed branch audio output will be written here
    var numChannels : Int             = 0   // of the result signal
  }

  /** Structural data for each case of an if-then-else block, i.e.
    * the corresponding graph element, a link back to the predecessor case
    * and the top structure, a bit-mask for the sum of the preceding cases
    * and the index of this branch (counting from zero as the first case).
    */
  private[synth] final class ExpIfCase(val peer: Then[Any], val pred: Option[ExpIfCase], val top: ExpIfTop,
                        val predMask: Int, val branchIdx: Int) {
    /** If true, the return signal of this branch is requested. */
    var resultUsed: Boolean = false
    val thisMask  : Int     = predMask | (1 << branchIdx)
  }

  /** The currently active builder, taking from a thread local variable. */
  def get: NestedUGenGraphBuilder = UGenGraph.builder match {
    case b: NestedUGenGraphBuilder => b
    case _ => sys.error("Cannot expand modular If-block outside of NestedUGenGraphBuilder")
  }

  /** A result contains the identifier of this partial
    * graph (used for constructing the control for pausing
    * the node if not `-1`), the UGen graph, the buses
    * used, and children from if-then-else blocks (if any).
    *
    * When building the set of synths, the children of
    * the tree formed by a `Result` should be arranged in
    * depth-first order.
    */
  trait Result {
    /** For every child whose `id` is greater than
      * or equal to zero, a control must be set
      * based on `pauseNodeCtlName`.
      */
    def id: Int

    def graph: UGenGraph

    /** Outgoing links.
      * To "play" the result, for each link
      * a corresponding bus must be allocated
      * and set through a control obtained
      * with `linkCtlName(id)`.
      */
    def links: List[Link]

    /** For each child, a synth must be created and nested
      * in the parent group. The synth's id must be
      * communicated through `pauseNodeCtlName`
      */
    def children: List[Result]
  }

  /** Expands a synth graph with the default nesting graph builder. */
  def build(graph: SynthGraph): Result = {
    val b = new OuterImpl
    b.build(graph)
  }

  /** Control for communicating the node index of a child, needed for pausing it from a parent node. */
  private[synth] def pauseNodeCtlName(id: Int): String =
    s"$$if$id" // e.g. first if block third branch is `$if0_2n`

  /** Single control for setting the bus index of a `Link`. */
  private[synth] def linkCtlName(id: Int): String =
    s"$$ln$id"

  private def isBinary(in: GE): Boolean = {
    import BinaryOpUGen._
    in match {
      case Constant(c) => c == 0 || c == 1
      case BinaryOpUGen(op, a, b) =>
        val opi = op.id
        // if (op == Eq || op == Neq || op == Lt || op == Gt || op == Leq || op == Geq) true
        if (opi >= Eq.id && opi <= Geq.id) true
        // else if (op == BitAnd || op == BitOr || op == BitXor) isBinary(a) && isBinary(b)
        else if (opi >= BitAnd.id && opi <= BitXor.id) isBinary(a) && isBinary(b)
        else false

      case UnaryOpUGen(UnaryOpUGen.Not, _) | _: ToggleFF | _: SetResetFF | _: Trig1 | _: Schmidt | _: Done => true
      case _ => false
    }
  }

  /* Ensures a graph element outputs either zero or one. If the element
   * is not a known binary signal, this is done by comparing with `sig_!= 0`.
   */
  private def forceBinary(in: GE): GE = if (isBinary(in)) in else in sig_!= 0

  /* Creates the graph elements on the sink side of a link, i.e.
   * a control for the bus index and an `In` UGen.
   */
  private def expandLinkSink(link: Link): UGenInLike = {
    val ctlName   = linkCtlName(link.id)
    val ctl       = ctlName.ir    // link-bus
    val in        = In(link.rate, bus = ctl, numChannels = link.numChannels)
    val inExp = in.expand
    // (link, inExp)
    inExp
  }

  private final case class ResultImpl(id: Int, graph: UGenGraph, links: List[Link],
                                      children: List[Result]) extends Result

  /*
    TODO:

    - handle controls over boundaries

   */
  trait Basic extends NestedUGenGraphBuilder with BasicUGenGraphBuilder with SynthGraph.Builder {
    builder =>

    // ---- abstract ----

    def outer: Outer
    protected def parent: Basic
    protected def childId: Int

    /** Corresponding if-case or `None` if not part of an if-block. */
    protected def thisExpIfCase: Option[ExpIfCase]

    protected def mkInner(childId: Int, thisExpIfCase: Option[ExpIfCase], parent: Basic, name: String): Inner

    // ---- impl ----

    // N.B. some `private[this]` had to be changed to `private`
    // because otherwise scalac 2.10.6 crashes

    protected final var _children = List.empty[Result]    // "reverse-sorted"
    protected final var _links    = List.empty[Link]      // "reverse-sorted"

    private /* [this] */ var expIfTops   = List.empty[ExpIfTop]  // "reverse-sorted"; could be a Set but prefer determinism

    private[this] var sources         = Vec.empty[Lazy]
    private[this] var controlProxies  = ISet.empty[ControlProxyLike]

    override def toString = s"SynthGraph.Builder@${hashCode.toHexString}"

    private def errorOutsideBranch(): Nothing =
      throw new UnsupportedOperationException("ThisBranch used outside of if-branch")

    def thisBranch: GE = {
      val c     = thisExpIfCase                .getOrElse(errorOutsideBranch())
      val in0   = parent.tryRefer("sel-branch").getOrElse(errorOutsideBranch())
      val top   = c.top
      if (top.hasLag) {
        // we don't know if we are the last branch, so simply skip that optimization
        import c.branchIdx
        val lastBranchIdx = top.branchCount - 1
        val condMask = if (branchIdx == lastBranchIdx) in0 else in0 & c.thisMask
        if (branchIdx == 0) condMask else condMask sig_== (1 << branchIdx)
      } else {
        in0
      }
    }

    final def buildInner(g0: SynthGraph): Result = {
      var _sources: Vec[Lazy] = g0.sources
      controlProxies = g0.controlProxies
      do {
        sources = Vector.empty
        val ctlProxiesCpy = controlProxies
        var i = 0
        val sz = _sources.size
        while (i < sz) {
          val source = _sources(i)
          source.force(builder)
          // if we just expanded an if-block, we must now
          // wrap the remaining sources in a new child, because
          // only that way we can correctly establish a link
          // between the if-block's return signal and its
          // dependents.
          source match {
            case _: ElseGE =>
              // XXX TODO --- what to do with the damn control proxies?
              val graphC    = SynthGraph(sources = _sources.drop(i + 1), controlProxies = ctlProxiesCpy)
              val child     = mkInner(childId = -1, thisExpIfCase = None, parent = builder, name = "continue")
              // WARNING: call `.build` first before prepending to `_children`,
              // because _children might be updated during `.build` now.
              // Thus _not_ `_children = _children :: child.build(graphC)`!
              val childRes  = child.build(graphC)
              _children   ::= childRes
              i = sz    // "skip rest" in the outer graph
            case _ =>
          }
          i += 1
        }
        _sources = sources

        // at the very end, check for deferred if-blocks;
        // those are blocks whose result hasn't been used,
        // i.e. side-effecting only.
        if (_sources.isEmpty && expIfTops.nonEmpty) {
          val xs = expIfTops
          expIfTops = Nil
          xs.reverse.foreach { expTop =>
            expandIfCases(expTop)
          }
          _sources = sources
        }

      } while (_sources.nonEmpty)
      val ugenGraph = build(controlProxies)

      ResultImpl(id = childId, graph = ugenGraph, links = _links.reverse, children = _children.reverse)
    }

    final def build(g0: SynthGraph): Result = run(buildInner(g0))

    final def run[A](thunk: => A): A = SynthGraph.use(builder) {
      UGenGraph.use(builder) {
        thunk
      }
    }

    // ---- SynthGraph.Builder ----

    final def addLazy(g: Lazy): Unit = sources :+= g

    final def addControlProxy(proxy: ControlProxyLike): Unit = controlProxies += proxy

    // ---- internal ----

    private /* [this] */ var linkMap = Map.empty[AnyRef, Link]

    // XXX TODO --- do we need to repeat with parent.tryRefer in the case of `None`?
    final def tryRefer(ref: AnyRef): Option[UGenInLike] =
      sourceMap.get(ref).collect {
        case sig: UGenInLike =>
          val link        = linkMap.getOrElse(ref, {
            val numChannels = sig.outputs.size
            val linkId      = outer.allocId()
            // an undefined rate - which we forbid - can only occur with mixed UGenInGroup
            val linkRate    = sig.rate match {
              case `scalar` => control
              case r: Rate  => r
              case _ => throw new IllegalArgumentException("Cannot refer to UGen group with mixed rates across branches")
            }
            val res         = Link(id = linkId, rate = linkRate, numChannels = numChannels)
            addLink(ref, res)
            val ctlName     = linkCtlName(linkId)
            // Add a control and `Out` to this (parent) graph.
            // This is super tricky -- we have to encapsulate
            // in a new synth graph because otherwise GE will
            // end up in the caller's synth graph; there is
            // no way we can catch them in our own outer synth graph,
            // so we must then force them explicitly!
            run {
              val ctl = ctlName.ir    // link-bus
              Out(linkRate, bus = ctl, in = sig)
            }
            res
          })
          expandLinkSink(link)

        case expIfCase: ExpIfCase =>
          // mark this case and all its predecessors as result-used.
          @tailrec def loop(c: ExpIfCase): Unit = if (!c.resultUsed) {
            c.resultUsed = true
            c.pred match {
              case Some(p) => loop(p)
              case _ =>
            }
          }
          loop(expIfCase)

          val expTop = expIfCase.top
          import expTop._
          if (resultLinkId < 0) resultLinkId = outer.allocId()
          run {
            expandIfCases(expTop)
          }
          expIfTops = expIfTops.filterNot(_ == expTop)    // remove from repeated expansion

          assert(numChannels > 0)
          // println(s"expTop.numChannels = $numChannels")
          val link = Link(id = resultLinkId, rate = audio, numChannels = numChannels)
          addLink(ref, link)

          expandLinkSink(link)
      }

    private def addLink(ref: AnyRef, link: Link): Unit = {
      linkMap += ref -> link
      _links ::= link
    }

    // ---- UGenGraph.Builder ----

    def expandIfCase(c: Then[Any]): ExpIfCase = {
      // create a new ExpIfCase
      val res: ExpIfCase = c match {
        case i: IfThenLike[Any] =>
          val expTop = new ExpIfTop(lagTime = i.dur, selBranchId = outer.allocId())
          expIfTops ::= expTop
          new ExpIfCase(peer = i, top = expTop, pred = None, predMask = 0, branchIdx = 0)

        case e: ElseOrElseIfThen[Any] =>
          val expPar = e.pred.visit(this)
          val expTop = expPar.top
          new ExpIfCase(peer = e, top = expTop, pred = Some(expPar),
            predMask = expPar.predMask | (1 << expPar.branchIdx), branchIdx = expTop.branchCount)
      }

      // register it with the top
      val top = res.top
      top.branchCount += 1
      require(top.branchCount < 24, s"If -- number of branches cannot be >= 24")
      top.branches ::= res

      // make sure the condition is zero or one
      val condBin = forceBinary(c.cond)
      top.condAcc = if (res.branchIdx == 0) {
        condBin
      } else {
        // then "bit-shift" it.
        val condShift = condBin << res.branchIdx
        // then collect the bits in a "bit-field"
        top.condAcc | condShift
      }

      res
    }

    private def expandIfCases(expTop: ExpIfTop): Unit = {
      // This is very elegant: The following elements
      // are side-effect free and will thus be removed
      // from the UGen graph, _unless_ a child is asking
      // for this through `tryRefer`, creating the
      // link on demand. We must store in the source-map
      // _before_ we create the children.

      // the signal written to the branch-selector bus
      // depends on whether we use `If` or `IfLag`.
      //
      // - in the former case, each branch sees the same
      //   trigger signal that is an impulse indicating the
      //   branch has changed. since the branch is only
      //   resumed when it is active, each branch can use
      //   that signal directly without any risk of confusion.
      // - in the latter case, each branch will have a
      //   "release" phase in which already a different branch
      //   condition holds. in order to make it possible to
      //   react to that release, we have to generate a gate
      //   signal instead. we send the delayed `condAcc` to
      //   the bus, and each branch then compares that to its
      //   own branch index.
      import expTop.{hasLag => _hasLag, _}

      // Note: Delay1 does not initialize its state with zero,
      // therefore we have to add a zero frequency impulse.
//      val condChange = (Delay1.kr(condAcc) sig_!= condAcc) + Impulse.kr(0)
      // Note: OneZero(_, 1) behaves like a correct Delay1 with zero initial state
      val condChange = OneZero.kr(condAcc, 1) sig_!= condAcc

      val (selBranchSig, condAccT) = if (_hasLag) {
        import ugen._
        // freeze lag time at scalar rate, and ensure it is at
        // least `ControlDur`.
        val lagTimeI = lagTime.rate match {
          case `scalar` => lagTime
          case _        => DC.kr(lagTime)
        }
        val lagTimeM    = lagTimeI.max(ControlDur.ir)

        val condChDly   = TDelay    .kr(condChange, lagTimeM  )
        val condChHold  = SetResetFF.kr(condChange, condChDly )
        val heldAcc     = Latch     .kr(condAcc   , condChHold)

        // DelayN starts with zeroed buffer; we simply add the
        // latched un-delayed beginning during the buffer-fill-up.
        val heldDly0    = DelayN.kr(heldAcc, lagTimeM, lagTimeM)
        val heldDly     = heldDly0 + heldAcc * (heldDly0 sig_== 0)
        (heldAcc, heldDly)

      } else {
        (condChange, condAcc)
      }

      // XXX TODO --- should we use different keys, or
      // is this safe because we'll encounter all the
      // corresponding `ThisBranch` instances right here
      // before leaving the method?
      sourceMap += "sel-branch" -> selBranchSig.expand

      // ----

      // numChannels: Will be maximum across all branches.
      // Note that branches with a smaller number
      // of channels do not "wrap-extend" their
      // signal as would be the case normally in
      // ScalaCollider. Instead, they simply do
      // not contribute to the higher channels.
      // We can add the other behaviour later.

      val lastBranchIdx = branchCount - 1

      branches.reverse.foreach { c =>
        import ugen._
        // the branch condition is met when the fields masked up to here equal the shifted single condition
        val condMask  = if (c.branchIdx == lastBranchIdx) condAccT else condAccT & c.thisMask
        val condEq    = if (c.branchIdx == 0)             condMask else condMask sig_== (1 << c.branchIdx)

        val childId = outer.allocId()
        val nodeCtl = pauseNodeCtlName(childId)
        // condEq.poll(4, s"gate $branchIdx")
        Pause.kr(gate = condEq, node = nodeCtl.ir)

        val graphBranch = if (!c.resultUsed) c.peer.branch else {
          val e = c.peer.asInstanceOf[Then[GE]] // XXX TODO --- not pretty the cast
          val graphTail = SynthGraph {
            val resultCtl   = linkCtlName(resultLinkId)
            val resultBus   = resultCtl.ir
            BranchOut(expTop, resultBus, e.result)
          }
          val graphHead = e.branch
          graphHead.copy(sources = graphHead.sources ++ graphTail.sources,
            controlProxies = graphHead.controlProxies ++ graphTail.controlProxies)
        }

        // now call `UGenGraph.use()` with a child builder, and expand `graphBranch`
        val child   = mkInner(childId = childId, thisExpIfCase = Some(c),
          parent = builder, name = s"inner{if $resultLinkId case ${c.branchIdx}")
        val childRes = child.build(graphBranch)
        _children ::= childRes
      }
    }
  }

  private sealed trait Impl extends Basic {
    final protected def mkInner(childId: Int, thisExpIfCase: Option[ExpIfCase], parent: Basic,
                                name: String): Inner =
      new InnerImpl(childId = childId, thisExpIfCase = thisExpIfCase, parent = parent, name = name)
  }

  private def smartRef(ref: AnyRef): String = {
    val t = new Throwable
    t.fillInStackTrace()
    val trace = t.getStackTrace
    val opt = trace.collectFirst {
      case ste if (ste.getMethodName == "force" || ste.getMethodName == "expand") && ste.getFileName != "Lazy.scala" =>
        val clz = ste.getClassName
        val i   = clz.lastIndexOf(".") + 1
        val j   = clz.lastIndexOf("@", i)
        val s   = if (j < 0) clz.substring(i) else clz.substring(i, j)
        s"$s@${ref.hashCode().toHexString}"
    }
    opt.getOrElse(ref.hashCode.toHexString)
  }

  private final class InnerImpl(val childId: Int, val thisExpIfCase: Option[ExpIfCase],
                                val parent: Basic, protected val name: String)
    extends Inner with Impl

  trait Inner
    extends Basic {

    // ---- abstract ----

    protected def name: String

    // ---- impl ----

    final def outer: Outer = parent.outer

    override def toString: String = name

    override final def visit[U](ref: AnyRef, init: => U): U = visit1[U](ref, () => init)

    private def visit1[U](ref: AnyRef, init: () => U): U = {
      // log(s"visit  ${ref.hashCode.toHexString}")
      sourceMap.getOrElse(ref, {
        log(this, s"expand ${smartRef(ref)}...")
        val exp: Any = parent.tryRefer(ref).getOrElse {
          log(this, s"...${smartRef(ref)} -> not yet found")
          init()
        }
        sourceMap += ref -> exp
        log(this, s"...${smartRef(ref)} -> ${exp.hashCode.toHexString} ${printSmart(exp)}")
        exp
      }).asInstanceOf[U] // not so pretty...
    }
  }

  private final class OuterImpl extends Outer with Impl

  trait Outer extends Basic {
    builder =>

    final def outer         : Outer             = this
    final def parent        : Basic             = this
    final def thisExpIfCase : Option[ExpIfCase] = None
    final def childId       : Int               = -1

    override def toString = "outer"

    private[this] var idCount = 0

    /** Allocates a unique increasing identifier. */
    final def allocId(): Int = {
      val res = idCount
      idCount += 1
      res
    }

    override final def visit[U](ref: AnyRef, init: => U): U = {
      log(this, s"visit  ${smartRef(ref)}")
      sourceMap.getOrElse(ref, {
        log(this, s"expand ${smartRef(ref)}...")
        val exp = init
        log(this, s"...${smartRef(ref)} -> ${exp.hashCode.toHexString} ${printSmart(exp)}")
        sourceMap += ref -> exp
        exp
      }).asInstanceOf[U] // not so pretty...
    }
  }

  private def printSmart(x: Any): String = x match {
    case u: UGen  => u.name
    case _        => x.toString
  }

  final var showLog = false

  @elidable(elidable.CONFIG) private def log(builder: Basic, what: => String): Unit =
    if (showLog) println(s"ScalaCollider-DOT <${builder.toString}> $what")
}

/** A UGen graph builder that supports the registration of
  * the `If-ElseIf-Then-Else` elements, building not a single
  * graph but a tree of related graphs.
  */
trait NestedUGenGraphBuilder extends UGenGraph.Builder {
  import NestedUGenGraphBuilder.ExpIfCase

  /** Returns gate that is open when this if branch is selected. */
  def thisBranch: GE

  def expandIfCase(c: Then[Any]): ExpIfCase
}