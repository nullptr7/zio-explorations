package com.github.nullptr7
package playground

object ContextualFunctions extends App:
  final private case class Foo(s: String)
  final private case class Bar(i: Int)

  private def definitionOnHowToCreateFooAndBar(xyzzy: (Foo, Bar) ?=> String): Unit =
    val foo = Foo("waldo")
    val bar = Bar(2)
    println(xyzzy(using foo, bar))

  private def implementationOnHowToUseFooAndBarAndGetDesiredOutput(using Foo, Bar): String =
    val foo = summon[Foo]
    val bar = summon[Bar]
    s"Method - foo: $foo, bar: $bar"

  definitionOnHowToCreateFooAndBar(implementationOnHowToUseFooAndBarAndGetDesiredOutput)
