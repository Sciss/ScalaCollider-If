package de.sciss.synth

/** This has been tested with SuperCollider server 3.7.0 on Linux.
  *
  * Not yet tested: nested `BranchIf`
  */
class NestedSuite extends SuperColliderSpec {
  val freq = 441.0

  "A synth graph with a sine oscillator" should "produce the predicted sound output" in {
    val g = SynthGraph {
      import ugen._
      Out.ar(0, SinOsc.ar(freq))
    }

    val r     = render(g)
    val len   = 1024  // should be on block boundary!
    r.send(len, r.node.freeMsg)
    r.run().map { arr =>
      val arr0  = arr(0)
      val man   = mkSine(freq, 1, len)
      assertSameSignal(arr0, man)
    }
  }

  "A synth graph with an unit result If Then" should "produce the predicted sound output" in {
    val g = SynthGraph {
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
        mkSine(freq = freq, startFrame = 1         , len = period) ++ mkSilent(period) ++
        mkSine(freq = freq, startFrame = period + 1, len = period) ++ mkSilent(period)
      assertSameSignal(arr0, man)
    }
  }

  "A synth graph with an unit result If Then ElseIf Then" should "produce the predicted sound output" in {
    val g = SynthGraph {
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
        mkSine(freq = freq, startFrame = 1        , len = period) ++ mkSilent(period) ++
        mkSine(freq = freq, startFrame = period + 1, len = period) ++ mkSilent(period)
      val man1  =
        mkSilent(period) ++ mkSine(freq = freq, startFrame = 1         , len = period) ++
        mkSilent(period) ++ mkSine(freq = freq, startFrame = period + 1, len = period)
      assertSameSignal(arr0, man0)
      assertSameSignal(arr1, man1)
    }
  }

  "A synth graph with an unit result If Then Else" should "produce the predicted sound output" in {
    val g = SynthGraph {
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
        mkSine(freq = freq, startFrame = 1         , len = period) ++ mkSilent(period) ++
        mkSine(freq = freq, startFrame = period + 1, len = period) ++ mkSilent(period)
      val man1  =
        mkSilent(period) ++ mkSine(freq = freq, startFrame = 1         , len = period) ++
        mkSilent(period) ++ mkSine(freq = freq, startFrame = period + 1, len = period)
      assertSameSignal(arr0, man0)
      assertSameSignal(arr1, man1)
    }
  }

  // this actually also tests propagation of signals (`fr`) from top to children
  "A synth graph with an GE result If Then Else" should "produce the predicted sound output" in {
    val g = SynthGraph {
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
        mkSine(freq = freq, startFrame = 1         , len = period) ++ mkSilent(period) ++
        mkSine(freq = freq, startFrame = period + 1, len = period) ++ mkSilent(period)
      val man1  =
        mkSilent(period) ++ mkSine(freq = freq, startFrame = 1         , len = period) ++
        mkSilent(period) ++ mkSine(freq = freq, startFrame = period + 1, len = period)
      assertSameSignal(arr0, man0)
      assertSameSignal(arr1, man1)
    }
  }

  // this actually also tests non-binary conditional signals
  "A synth graph with nested If blocks" should "produce the predicted sound output" in {
    val g = SynthGraph {
      import ugen._
      val tr    = Impulse.kr(ControlRate.ir / 5)
      // it will output 1, 2, 3, 4
      val step  = Stepper.kr(trig = tr, lo = 0, hi = 10)
      // if we leave this a k-rate, the SinOsc in the inner branch somehow
      // picks up an init value of 2 instead of 3, interpolating weird for one control-block
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
        mkSine(freq = freq    , startFrame = 1, len = period) ++ mkSilent(period) ++
        mkSine(freq = freq * 3, startFrame = 0, len = period) ++ mkSilent(period)
      val man1  =
        mkSilent(period) ++ mkSine(freq = freq, startFrame = 1         , len = period) ++
        mkSilent(period) ++ mkSine(freq = freq, startFrame = period + 1, len = period)
      // printVector(arr0)
      assertSameSignal(arr0, man0)
      assertSameSignal(arr1, man1)
    }
  }

  "A synth graph with an If block and ThisBranch element" should "produce the predicted sound output" in {
    val g = SynthGraph {
      import ugen._
      val tr    = Impulse.kr(ControlRate.ir / 5)
      val ff    = ToggleFF.kr(tr)
      val res   = If (ff) Then {
        val b   = ThisBranch()
        val bA  = Latch.ar(b, Impulse.ar(ControlRate.ir))
        Seq(bA, DC.ar(0)): GE
      } Else {
        val b   = ThisBranch()
        val bA  = Latch.ar(b, Impulse.ar(ControlRate.ir))
        Seq(DC.ar(0), bA): GE
      }
      Out.ar(0, res)
    }

    val r     = render(g, numChannels = 2)
    val len   = blockSize * 20
    r.send(len, r.node.freeMsg)
    r.run().map { arr =>
      val arr0    = arr(0)
      val arr1    = arr(1)
      val period  = blockSize * 5
      val spike   = mkConstant(1.0, blockSize) ++ mkSilent(period - blockSize)
      val silent  = mkSilent(period)
      val man0  = spike  ++ silent ++ spike  ++ silent
      val man1  = silent ++ spike  ++ silent ++ spike
      // printVector(arr0)
      // printVector(arr1)
      assertSameSignal(arr0, man0)
      assertSameSignal(arr1, man1)
    }
  }

  "A synth graph with an IfLag block" should "produce the predicted sound output" in {
    val g = SynthGraph {
      import ugen._
      val tr    = Impulse.kr(ControlRate.ir / 15)
      val ff    = ToggleFF.kr(tr)
      val dur   = (blockSize * 5) / sampleRate
      val res   = IfLag (ff, dur) Then {
        val gate  = ThisBranch()
        val env   = EnvGen.ar(Env.asr(attack = dur, release = dur, curve = Curve.lin), gate)
        Seq(env, DC.ar(0)): GE
      } Else {
        val gate  = ThisBranch()
        val env   = EnvGen.ar(Env.asr(attack = dur, release = dur, curve = Curve.lin), gate)
        Seq(DC.ar(0), env): GE
      }
      Out.ar(0, res)
    }

    val r       = render(g, numChannels = 2)
    val segm    = blockSize * 5
    val period  = segm * 3
    val len     = period * 4 + segm
    r.send(len, r.node.freeMsg)
    r.run().map { arr =>
      val arr0    = arr(0)
      val arr1    = arr(1)
      // note: because the first ever activated branch does not
      // have to wait for the previous branch to fade out, it
      // actually has a longer sustain period
      // (see SysSon technical report Sep 2016).
      val env0    = mkLine(segm, start = 0f, end = 1f, startFrame = 1) ++ mkConstant(1f, segm * 2) ++
                    mkLine(segm, start = 1f, end = 0f)
      val env     = mkLine(segm, start = 0f, end = 1f)                 ++ mkConstant(1f, segm)     ++
                    mkLine(segm, start = 1f, end = 0f)
      val silent  = mkSilent(period)
      val silent0 = mkSilent(period + segm)
      val man0  = env0    ++ silent ++ env    ++ silent
      val man1  = silent0 ++ env    ++ silent ++ env
//      printVector(man0)
//      printVector(arr0)
//      printVector(arr1)
      assertSameSignal(arr0, man0)
      assertSameSignal(arr1, man1)
    }
  }
}