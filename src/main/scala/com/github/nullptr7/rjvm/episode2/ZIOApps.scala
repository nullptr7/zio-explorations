package com.github.nullptr7
package rjvm
package episode2

import zio.*

object ZIOApps:
  val meaningOfLine: UIO[Int] = ZIO.succeed(42)

  def main(args: Array[String]): Unit =
    val runtime = Runtime.default
    given trace: Trace = Trace.empty
    Unsafe.unsafeCompat { unsafe =>
      given u: Unsafe = unsafe
      println(runtime.unsafe.run(meaningOfLine))
    }

object BetterZIOApp extends ZIOAppDefault:
  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    ZIOApps.meaningOfLine.flatMap(x => ZIO.succeed(println(x)))

object ManualApp extends ZIOApp:
  override def bootstrap: ZLayer[ZIOAppArgs, Any, Environment] = ???

  override type Environment = this.type

  override def run: ZIO[Environment & (ZIOAppArgs & Scope), Any, Any] = ???

  override implicit def environmentTag: izumi.reflect.Tag[Environment] = ???
