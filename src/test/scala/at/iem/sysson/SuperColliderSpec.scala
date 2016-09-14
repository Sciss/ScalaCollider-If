package at.iem.sysson

import java.io.RandomAccessFile
import java.nio.ByteBuffer

import de.sciss.file._
import de.sciss.synth.io.AudioFile
import de.sciss.synth.{NestedUGenGraphBuilder, Server, SynthGraph, addToHead}
import de.sciss.{osc, synth}
import org.scalatest.{AsyncFlatSpec, FutureOutcome, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, blocking}
import scala.util.Try

abstract class SuperColliderSpec extends AsyncFlatSpec with Matchers {
  // ---- abstract ----

  def blockSize : Int     = 64
  def sampleRate: Double  = 44100

  // ---- utility ----

  final def printVector(arr: Array[Float]): Unit =
    println(arr.mkString("Vector(", ",", ")"))

  final def mkSine(freq: Double, startFrame: Int, len: Int, sampleRate: Double = sampleRate): Array[Float] = {
    // SinOsc drops first sample. Hello SuperCollider!
    val off   = startFrame + 1
    val freqN = 2 * math.Pi * freq / sampleRate
    Array.tabulate(len)(i => math.sin((off + i) * freqN).toFloat)
  }

  final def mkSilent(len: Int): Array[Float] = new Array[Float](len)

  // ---- impl ----

  final def sampleRateI: Int = {
    val res = sampleRate.toInt
    require (res == sampleRate)
    res
  }

  final def assertSameSignal(a: Array[Float], b: Array[Float], tol: Float = 1.0e-4f) = {
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
      var bundles = b :: osc.Bundle.now(s.defaultGroup.newMsg(s.rootNode, addToHead)) :: Nil
      val node    = n

      def lastSec: Double = bundles.head.timetag.toSecs

      def send(frame: Long, message: osc.Message): Unit = {
        val sec = frame.toDouble / sampleRate
        if (sec < lastSec)
          throw new IllegalArgumentException(s"Time tags must be monotonously increasing ($sec < $lastSec)")
        bundles ::= osc.Bundle.secs(sec, message)
      }

      import scala.concurrent.ExecutionContext.Implicits.global

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