package com.github.nullptr7
package rjvm
package episode3

import zio.*
import utils.*

object Interruptions extends ZIOAppDefault:
  private val zioWithTime =
    (ZIO.succeed("Starting computation").debugThread *>
      ZIO.sleep(2.seconds) *>
      ZIO.succeed(42).debugThread).onInterrupt(ZIO.succeed("I was interrupted!").debugThread)

  private val anInterruptions =
    for
      fib    <- zioWithTime.fork
      _      <- ZIO.sleep(1.second) *> ZIO.succeed("Interrupting!").debugThread *> fib.interrupt /* <-- This is an effect, blocks the calling fiber until the interrupted fiber is done or interrupted */
      _      <- ZIO.succeed("Interruption successful").debugThread
      result <- fib.join
    yield result

  /*
    In this case since we are putting this entire thing in fork. Hence only this fork will be blocked
    (ZIO.sleep(1.second) *> ZIO.succeed("Interrupting!").debugThread *> fib.interrupt).fork
    However, notice that this fiber is a leaked fiber since we are technically joining it.
    Although, this is fine... since fiber's lifecycle is very short. Hence leaking fiber is fine.
   */
  private val anInterruptions_v2 =
    for
      fib    <- zioWithTime.fork
      _      <- (ZIO.sleep(1.second) *> ZIO.succeed("Interrupting!").debugThread *> fib.interrupt).fork    /* In this the entire call is not blocked only this fiber, hence the for comprehension can still move forward */
      _      <- ZIO.sleep(1.second) *> ZIO.succeed("Interrupting Again!").debugThread *> fib.interruptFork /* This is another way*/
      _      <- ZIO.succeed("Interruption successful").debugThread
      result <- fib.join
    yield result

  private val nonInterruptions =
    for
      fib <- zioWithTime.fork
      _   <- ZIO.succeed("Parent completed")
    yield ()

  /*
    Automatic interruptions
   */
  // outliving a parent fiber
  /*
    here the child fiber takes longer time to complete rather than its Parent
    So in this case, if the parent dies the child automatically dies irrespective it has completed its work or not.
   */
  val parentEffect =
    ZIO.succeed("Spwaning fiber").debugThread *>
      zioWithTime.fork *>                          // child fiber
      ZIO.sleep(1.second) *>
      ZIO.succeed("Parent successful").debugThread // done here

  // If you want to uproot the child fiber from the current parent, we can uproot and create a child of the main application

  val parentEffect_v2 =
    ZIO.succeed("Spwaning fiber").debugThread *>
      zioWithTime.forkDaemon *>                    // this child fiber is child of main application instead
      ZIO.sleep(1.second) *>
      ZIO.succeed("Parent successful").debugThread // done here

  val testOutLivingParent =
    for
      parentEffectFib <- parentEffect.fork
      _               <- ZIO.sleep(3.seconds)
      _               <- parentEffectFib.join
    yield ()

  val testOutLivingParent_v2 =
    for
      parentEffectFib <- parentEffect_v2.fork
      _               <- ZIO.sleep(3.seconds)
      _               <- parentEffectFib.join
    yield ()

  // Another situation, where fiber maybe automatically interrupted is RACING
  val slowEffect = (ZIO.sleep(2.seconds) *> ZIO.succeed("slow")).debugThread.onInterrupt(ZIO.succeed("[slow] interrupted").debugThread)
  val fastEffect = (ZIO.sleep(1.second) *> ZIO.succeed("fast")).debugThread.onInterrupt(ZIO.succeed("[fast] interrupted").debugThread)

  val aRace    = fastEffect.race(slowEffect)
  val testRace = aRace.fork *> ZIO.sleep(3.seconds)

  /** Exercise:
    */
  /* 1. Implement a timeout function
      - if ZIO is successful before timeout => a successful effect
      - if ZIO fails before timeout => a failed effect
      - if ZIO takes longer than timeout => interrupt the effect
   */
  def timeout[R, E, A](zio: ZIO[R, E, A], time: Duration): ZIO[R, E, A] = ???

  /* 2 - timeout v2 =>
      - if ZIO is successful before timeout => a successful effect containing SOME
      - if ZIO fails before timeout => a failed effect with E
      - if ZIO takes longer than timeout => interrupt the effect and return successful ZIO with NONE
    // hint = foldCauseZIO
   */
  def timeout_v2[R, E, A](zio: ZIO[R, E, A], time: Duration): ZIO[R, E, Option[A]] = ???

  override def run: ZIO[Any, Any, Any] = testRace
