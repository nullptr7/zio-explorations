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
  private val parentEffect =
    ZIO.succeed("Spwaning fiber").debugThread *>
      zioWithTime.fork *>                          // child fiber
      ZIO.sleep(1.second) *>
      ZIO.succeed("Parent successful").debugThread // done here

  // If you want to uproot the child fiber from the current parent, we can uproot and create a child of the main application

  private val parentEffect_v2 =
    ZIO.succeed("Spwaning fiber").debugThread *>
      zioWithTime.forkDaemon *>                    // this child fiber is child of main application instead
      ZIO.sleep(1.second) *>
      ZIO.succeed("Parent successful").debugThread // done here

  private val testOutLivingParent =
    for
      parentEffectFib <- parentEffect.fork
      _               <- ZIO.sleep(3.seconds)
      _               <- parentEffectFib.join
    yield ()

  private val testOutLivingParent_v2 =
    for
      parentEffectFib <- parentEffect_v2.fork
      _               <- ZIO.sleep(3.seconds)
      _               <- parentEffectFib.join
    yield ()

  // Another situation, where fiber maybe automatically interrupted is RACING
  private val slowEffect = (ZIO.sleep(2.seconds) *> ZIO.succeed("slow")).debugThread.onInterrupt(ZIO.succeed("[slow] interrupted").debugThread)
  private val fastEffect = (ZIO.sleep(1.second) *> ZIO.succeed("fast")).debugThread.onInterrupt(ZIO.succeed("[fast] interrupted").debugThread)

  private val aRace    = fastEffect.race(slowEffect)
  private val testRace = aRace.fork *> ZIO.sleep(3.seconds)

  /** Exercise:
    */
  /* 1. Implement a timeout function
      - if ZIO is successful before timeout => a successful effect
      - if ZIO fails before timeout => a failed effect
      - if ZIO takes longer than timeout => interrupt the effect
   */
  private def timeout[R, E, A](zio: ZIO[R, E, A], time: Duration): ZIO[R, E, A] =
    for
      fibZio <- zio.onInterrupt(ZIO.succeed("This ZIO is interrupted")).fork
      _      <- (ZIO.sleep(time) *> fibZio.interrupt).fork
      joined <- fibZio.join
    yield joined

  /* 2 - timeout v2 =>
      - if ZIO is successful before timeout => a successful effect containing SOME
      - if ZIO fails before timeout => a failed effect with E
      - if ZIO takes longer than timeout => interrupt the effect and return successful ZIO with NONE
    // hint = foldCauseZIO
   */
  private def timeout_v2[R, E, A](zio: ZIO[R, E, A], time: Duration): ZIO[R, E, Option[A]] =
    /*
    for
      fibZio <- zio
                  .map(Option(_))
                  .onInterrupt(ZIO.succeed(None))
                  .fork
      _      <- (ZIO.sleep(time) *> fibZio.interrupt).fork
      joined <- fibZio.join
    yield joined
     */

    timeout(zio, time)
      .foldCauseZIO(
        cause => if cause.isInterrupted then ZIO.succeed(None) else ZIO.failCause(cause),
        value => ZIO.succeed(Some(value)),
      )

  override def run: ZIO[Any, Any, Any] =

    val aZIO = ZIO.succeed("Starting...").debugThread *> ZIO.sleep(2.seconds) *> ZIO.succeed("I am done...").debugThread
    timeout_v2(aZIO, 4.seconds).debugThread
