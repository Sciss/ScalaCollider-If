package at.iem.sysson

import de.sciss.synth
import de.sciss.synth.SynthGraph

class NestedSuite extends SuperColliderSpec {
  val freq = 441.0

  "A synth graph with a sine oscillator" should "produce the predicted sound output" in {
    val g = SynthGraph {
      import synth._
      import ugen._
      Out.ar(0, SinOsc.ar(freq))
    }

    val r     = render(g)
    val len   = 1024  // should be on block boundary!
    r.send(len, r.node.freeMsg)
    r.run().map { arr =>
      val arr0  = arr(0)
      val man   = mkSine(freq, 0, len)
      assertSameSignal(arr0, man)
    }
  }

  "A synth graph with an unit result If Then" should "produce the predicted sound output" in {
    val g = SynthGraph {
      import synth._
      import ugen._
      val tr    = Impulse.kr(ControlRate.ir / 5)
      val ff    = ToggleFF.kr(tr)
      If (ff) Then {
        Out.ar(0, SinOsc.ar(freq))
      }
    }

    val r     = render(g)
    val len   = blockSize * 20
    r.send(len, r.node.freeMsg)
    r.run().map { arr =>
      val arr0  = arr(0)
      // println(arr0.mkString("Vector(", ",", ")"))
      val period = blockSize * 5
      // second sine start-frame is `period` not `2 * period` because it was (hopefully) paused!
      val man   =
        mkSine(freq = freq, startFrame = 0     , len = period) ++ mkSilent(period) ++
        mkSine(freq = freq, startFrame = period, len = period) ++ mkSilent(period)
      assertSameSignal(arr0, man)
    }
  }

  "A synth graph with an unit result If Then ElseIf Then" should "produce the predicted sound output" in {
    val g = SynthGraph {
      import synth._
      import ugen._
      val tr    = Impulse.kr(ControlRate.ir / 5)
      val ff    = ToggleFF.kr(tr)
      If (ff) Then {
        Out.ar(0, SinOsc.ar(freq))
      } ElseIf (!ff) Then {
        Out.ar(1, SinOsc.ar(freq))
      }
    }

    val r     = render(g, numChannels = 2)
    val len   = blockSize * 20
    r.send(len, r.node.freeMsg)
    r.run().map { arr =>
      val arr0  = arr(0)
      val arr1  = arr(1)
      val period = blockSize * 5
      val man0  =
        mkSine(freq = freq, startFrame = 0     , len = period) ++ mkSilent(period) ++
        mkSine(freq = freq, startFrame = period, len = period) ++ mkSilent(period)
      val man1  =
        mkSilent(period) ++ mkSine(freq = freq, startFrame = 0     , len = period) ++
        mkSilent(period) ++ mkSine(freq = freq, startFrame = period, len = period)
      assertSameSignal(arr0, man0)
      assertSameSignal(arr1, man1)
    }
  }

  "A synth graph with an unit result If Then Else" should "produce the predicted sound output" in {
    val g = SynthGraph {
      import synth._
      import ugen._
      val tr    = Impulse.kr(ControlRate.ir / 5)
      val ff    = ToggleFF.kr(tr)
      If (ff) Then {
        Out.ar(0, SinOsc.ar(freq))
      } Else {
        Out.ar(1, SinOsc.ar(freq))
      }
    }

    val r     = render(g, numChannels = 2)
    val len   = blockSize * 20
    r.send(len, r.node.freeMsg)
    r.run().map { arr =>
      val arr0  = arr(0)
      val arr1  = arr(1)
      val period = blockSize * 5
      val man0  =
        mkSine(freq = freq, startFrame = 0     , len = period) ++ mkSilent(period) ++
        mkSine(freq = freq, startFrame = period, len = period) ++ mkSilent(period)
      val man1  =
        mkSilent(period) ++ mkSine(freq = freq, startFrame = 0     , len = period) ++
        mkSilent(period) ++ mkSine(freq = freq, startFrame = period, len = period)
      assertSameSignal(arr0, man0)
      assertSameSignal(arr1, man1)
    }
  }

  // this actually also tests propagation of signals (`fr`) from top to children
  "A synth graph with an GE result If Then Else" should "produce the predicted sound output" in {
    val g = SynthGraph {
      import synth._
      import ugen._
      val tr    = Impulse.kr(ControlRate.ir / 5)
      val ff    = ToggleFF.kr(tr)
      val fr    = DC.ar(freq)
      val one   = fr / fr   // make sure `fr` is used in the outer scope
      val res   = If (ff) Then {
        Seq(SinOsc.ar(fr), DC.ar(0)): GE
      } Else {
        Seq(DC.ar(0), SinOsc.ar(fr)): GE
      }
      Out.ar(0, res * one)
    }

    val r     = render(g, numChannels = 2)
    val len   = blockSize * 20
    r.send(len, r.node.freeMsg)
    r.run().map { arr =>
      val arr0  = arr(0)
      val arr1  = arr(1)
      val period = blockSize * 5
      val man0  =
        mkSine(freq = freq, startFrame = 0     , len = period) ++ mkSilent(period) ++
        mkSine(freq = freq, startFrame = period, len = period) ++ mkSilent(period)
      val man1  =
        mkSilent(period) ++ mkSine(freq = freq, startFrame = 0     , len = period) ++
        mkSilent(period) ++ mkSine(freq = freq, startFrame = period, len = period)
      assertSameSignal(arr0, man0)
      assertSameSignal(arr1, man1)
    }
  }

  // this actually also tests non-binary conditional signals
  "A synth graph with nested If blocks" should "produce the predicted sound output" in {
    val g = SynthGraph {
      import synth._
      import ugen._
      val tr    = Impulse.kr(ControlRate.ir / 5)
      // it will output 1, 2, 3, 4
      val step  = Stepper.kr(trig = tr, lo = 0, hi = 10)
      val stepA = Latch.ar(step, Impulse.ar(ControlRate.ir))
      val fr    = DC.ar(freq)
      // first branch: true, false, true, false
      val res   = If (step % 2) Then {
        // first branch: true & true == true, false, true & false == false, false
        val res1 = If (step < 2) Then {
          Seq(SinOsc.ar(fr), DC.ar(0)): GE
        // second branch: true & false == false, false, true & true == true, false
        } Else {
          Seq(SinOsc.ar(fr * stepA), DC.ar(0)): GE
        }
        res1 * 0.5
      // second branch: false, true, false, true
      } Else {
        Seq(DC.ar(0), SinOsc.ar(fr) * 0.5): GE
      }
      Out.ar(0, res * 2)
    }

    val r     = render(g, numChannels = 2)
    val len   = blockSize * 20
    r.send(len, r.node.freeMsg)
    r.run().map { arr =>
      val arr0  = arr(0)
      val arr1  = arr(1)
      val period = blockSize * 5
      // crazy init states of SinOsc; now we do _not_ have to delay by one sample
      val man0  =
        mkSine(freq = freq    , startFrame =  0, len = period) ++ mkSilent(period) ++
        mkSine(freq = freq * 3, startFrame = -1, len = period) ++ mkSilent(period)
      val man1  =
        mkSilent(period) ++ mkSine(freq = freq, startFrame = 0     , len = period) ++
        mkSilent(period) ++ mkSine(freq = freq, startFrame = period, len = period)
      // printVector(arr0)
      assertSameSignal(arr0, man0)
      assertSameSignal(arr1, man1)
    }
  }
}