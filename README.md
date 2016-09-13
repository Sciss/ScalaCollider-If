# ScalaCollider-If

[![Build Status](https://travis-ci.org/iem-projects/ScalaCollider-If.svg?branch=master)](https://travis-ci.org/iem-projects/ScalaCollider-If)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/at.iem/scalacollider-if_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/at.iem/scalacollider-if_2.11)

A set of additional graph elements for [ScalaCollider](https://github.com/Sciss/ScalaCollider),
allowing the nesting of a graph in a resource efficient manner, by pausing the inactive branches.
This is facilitated by a new nested UGen graph builder that, unlike the standard builder, creates
an entire tree of UGen graphs to which the conditional branches are decomposed.
This project is (C)opyright 2016 by the Institute of Electronic Music and Acoustics (IEM), Graz.
Written by Hanns Holger Rutz.
This software is published under the GNU Lesser General Public License v2.1+.

## linking

The following artifact is available from Maven Central:

    "at.iem" %% "scalacollider-if" % v

The current stable version `v` is `"0.1.0"`.

## building

This project builds with sbt 0.13 and Scala 2.11, 2.10. To compile `sbt test:compile`.
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

Classes are placed in the conventional `de.sciss.synth` package,
matching with the other types and UGens of ScalaCollider.

## limitations

- currently, `ControlProxy` elements do not properly propagate from
  outer to inner scope.
- the number of cases per if-block is limited to 24
- while the compound result signal from an if-block uses the
  maximum of the number of channels used by each individual case,
  the signals of the cases are not "wrap-expanded" as in regular
  multi-channel-expansion. The user should thus ensure that all
  branches produce signals of the same number of channels.
