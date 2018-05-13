# ScalaCollider-If

[![Build Status](https://travis-ci.org/Sciss/ScalaCollider-If.svg?branch=master)](https://travis-ci.org/Sciss/ScalaCollider-If)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/de.sciss/scalacollider-if_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/de.sciss/scalacollider-if_2.11)

A set of additional graph elements for [ScalaCollider](https://github.com/Sciss/ScalaCollider),
allowing the nesting of a graph in a resource efficient manner, by pausing the inactive branches.
This is facilitated by a new nested UGen graph builder that, unlike the standard builder, creates
an entire tree of UGen graphs to which the conditional branches are decomposed.
This project is (C)opyright 2016&ndash;2017 by the Institute of Electronic Music and Acoustics (IEM), Graz.
(C)opyright 2017&ndash;2018 Hanns Holger Rutz.
Written by Hanns Holger Rutz.
This software is published under the GNU Lesser General Public License v2.1+.

## linking

The following artifact is available from Maven Central:

    "de.sciss" %% "scalacollider-if" % v

The current stable version `v` is `"0.8.0"`.

## building

This project builds with sbt and Scala 2.12, 2.11. To compile `sbt test:compile`.
To print the test output, `sbt test:run`.

## contributing

Please see the file [CONTRIBUTING.md](CONTRIBUTING.md)

## examples

A "side-effecting" block does not produce a dedicated return signal
but is used for UGens that have side effects. For example:

```scala
// ---- Unit result ----

If (freq > 100) Then {
  Out.ar(0, SinOsc.ar(freq))
}

If (freq > 100) Then {
  Out.ar(0, SinOsc.ar(freq))
} Else {
  freq.poll(0, "freq")
}
```

A block where each case evaluates to a sub-type of `GE` produces
a dedicated return signal that can then be further processed.
For example:

```scala
// ---- GE result ----

val res: GE = If (freq > 100) Then {
  SinOsc.ar(freq)
} Else {
  WhiteNoise.ar
}
Out.ar(0, res)

val res: GE = If (freq > 1000) Then {
  SinOsc.ar(freq)
} ElseIf (freq > 100) Then {
  Dust.ar(freq)
} Else {
  WhiteNoise.ar
}
Out.ar(0, res * 0.5)
```

If a branch refers to UGens from the outer scope, for example the
`freq` element in `SinOsc.ar(freq)`, the UGen graph builder automatically
inserts link buses. Recursive nesting of if-then-else blocks is also
supported.

Each branch can check its state using the `ThisBranch()` graph element.
For a non-lagging `If` block, this creates a control signal trigger
each time the branch is activated. Because of problems in the current
SuperCollider versions with respect to initial states of triggers, this
signal might not work in all circumstances the first time the branch
is activated. For example `T2A.ar(ThisBranch())` will incorrectly
ignore the initial trigger. There are workarounds, for example using
`Latch`, which can be seen in the test suite.

Next to `If(cond) ...` there is an alternative element
`IfLag(cond, dur) ...` which makes it possible to fade out a branch
before it is deactivated. When `cond` becomes false, instead of pausing
the branch immediately, a delay of `dur` happens. This also affects
the newly selected branch, so there is no way of creating a cross-fade,
while preserving the CPU saving property that no two branches are
resumed at the same time. When using `IfLag`, the `ThisBranch` element
instead of producing a single pulse trigger when the branch becomes
active, it now provides a gate signal that remains high until the
branch is released. Therefore, the branch can use, for example, an
envelope generator that fades out no slower than `dur` when the
`ThisBranch` gate signal becomes low.

```scala
val dur = 0.5  // seconds
val res: GE = IfLag (freq > 1000, dur) Then {
  val gate = ThisBranch()
  val env = EnvGen.ar(Env.asr(release = dur), gate)
  SinOsc.ar(freq) * env
} Else {
  val gate = ThisBranch()
  val env = EnvGen.ar(Env.asr(release = dur), gate)
  WhiteNoise.ar * env
}
Out.ar(0, res)
```

Classes are placed in the conventional `de.sciss.synth` package,
matching with the other types and UGens of ScalaCollider.

For more background information and design considerations,
see the [sysson-experiments Project](https://github.com/iem-projects/sysson-experiments/releases/tag/if-then-else).

## limitations

- currently, `ControlProxy` elements do not properly propagate from
  outer to inner scope.
- the number of cases per if-block is limited to 24
- while the compound result signal from an if-block uses the
  maximum of the number of channels used by each individual case,
  the signals of the cases are not "wrap-expanded" as in regular
  multi-channel-expansion. The user should thus ensure that all
  branches produce signals of the same number of channels.
