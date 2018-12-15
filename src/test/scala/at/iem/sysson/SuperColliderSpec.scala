package at.iem.sysson

import java.io.RandomAccessFile
import java.nio.ByteBuffer

import de.sciss.file._
import de.sciss.synth.io.AudioFile
import de.sciss.synth.{NestedUGenGraphBuilder, Node, Server, SynthGraph, addToHead}
import de.sciss.{numbers, osc, synth}
import org.scalatest.{Assertion, AsyncFlatSpec, FutureOutcome, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, blocking}
import scala.util.Try

abstract class SuperColliderSpec extends AsyncFlatSpec with Matchers {
  // ---- abstract ----

  def blockSize : Int     = 64
  def sampleRate: Double  = 44100d

  // ---- utility ----

  final def printVector(arr: Array[Float]): Unit =
    println(arr.mkString("Vector(", ",", ")"))

  final def mkSine(freq: Double, startFrame: Int, len: Int, sampleRate: Double = sampleRate): Array[Float] = {
    val freqN = 2 * math.Pi * freq / sampleRate
    Array.tabulate(len)(i => math.sin((startFrame + i) * freqN).toFloat)
  }

  final def mkConstant(value: Float, len: Int): Array[Float] = Array.fill(len)(value)

  /** If `lineLen` is zero (default), it will be set to `len`. */
  final def mkLine(len: Int, start: Float = 0f, end: Float = 1f, startFrame: Int = 0, lineLen: Int = 0): Array[Float] = {
    val lineLen0 = if (lineLen == 0) len else lineLen
    import numbers.Implicits._
    Array.tabulate(len)(i => (i + startFrame).clip(0, lineLen0).linLin(0, lineLen0, start, end))
  }

  final def mkSilent(len: Int): Array[Float] = new Array(len)

  // ---- impl ----

  final def sampleRateI: Int = {
    val res = sampleRate.toInt
    require (res == sampleRate)
    res
  }

  final def assertSameSignal(a: Array[Float], b: Array[Float], tol: Float = 1.0e-4f): Assertion = {
    assert(a.length === b.length +- blockSize)
    val diff = (a, b).zipped.map((x, y) => math.abs(x - y))
    all (diff) should be < tol
  }

  trait Rendering {
    def node: synth.Node
    def send(frame: Long, message: osc.Message): Unit
    def run(): Future[Array[Array[Float]]]
  }

  final def render(g: SynthGraph, numChannels: Int = 1, sampleRate: Int = sampleRateI, blockSize: Int = blockSize,
             timeOut: Duration = 20.seconds): Rendering = {
    val s       = Server.dummy()
    val ug      = NestedUGenGraphBuilder.build(g)
    val (b, n)  = NestedUGenGraphBuilder.prepare(ug, s)

    new Rendering {
      var bundles: List[osc.Bundle] = b :: osc.Bundle.now(s.defaultGroup.newMsg(s.rootNode, addToHead)) :: Nil
      val node   : Node             = n

      def lastSec: Double = bundles.head.timeTag.toSecs

      def send(frame: Long, message: osc.Message): Unit = {
        val sec = frame.toDouble / sampleRate
        if (sec < lastSec)
          throw new IllegalArgumentException(s"Time tags must be monotonously increasing ($sec < $lastSec)")
        bundles ::= osc.Bundle.secs(sec, message)
      }

      // WARNING: ScalaTest AsyncTestSuite exposes its own ExecutionContext
      // which doesn't allow blocking and thereby causes the tests to hang.
      // In Scala 2.12, we must shadow the implicit by using the same name.
      implicit val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global

      def run(): Future[Array[Array[Float]]] = Future {
        val nrtCmdF = blocking(File.createTemp(suffix = ".osc"))
        val nrtOutF = blocking(File.createTemp(suffix = ".aif"))

        blocking {
          val nrtCmdR = new RandomAccessFile(nrtCmdF, "rw")
          val nrtCmdC = nrtCmdR.getChannel
          val bb      = ByteBuffer.allocate(65536)
          val c       = osc.PacketCodec().scsynth().build
          bundles.reverse.foreach { bndl =>
            bb.clear()
            bb.putInt(0)
            bndl.encode(c, bb)
            bb.flip()
            bb.putInt(0, bb.limit() - 4)
            nrtCmdC.write(bb)
          }
          nrtCmdR.close()
        }

        val config                = Server.Config()
        config.sampleRate         = sampleRate
        config.blockSize          = blockSize
        config.inputBusChannels   = 0
        config.outputBusChannels  = numChannels
        config.nrtCommandPath     = nrtCmdF.path
        config.nrtOutputPath      = nrtOutF.path
        val proc = Server.renderNRT(dur = lastSec, config = config)
        proc.start()

        Await.result(proc, timeOut)

        blocking {
          val af = AudioFile.openRead(nrtOutF)
          try {
            require(af.numFrames < 0x7FFFFFFF)
            val res = af.buffer(af.numFrames.toInt)
            af.read(res)
            res

          } finally {
            af.close()
          }
        }
      }
    }
  }

  override def withFixture(test: NoArgAsyncTest): FutureOutcome = {
    import sys.process._
    val scOk = Try(Seq("scsynth", "-v").!!).getOrElse("").startsWith("scsynth ")
    if (scOk) {
      test()
    } else {
      FutureOutcome.canceled("scsynth (SuperCollider) not found. Skipping test!")
    }
  }
}