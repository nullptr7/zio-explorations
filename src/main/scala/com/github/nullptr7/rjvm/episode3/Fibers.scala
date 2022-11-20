package com.github.nullptr7
package rjvm
package episode3

import zio.*
import utils.*
import zio.Exit.Success
import zio.Exit.Failure

object Fibers extends ZIOAppDefault:
  private val meaningOfLife = ZIO.succeed(42)
  private val favLanguage   = ZIO.succeed("Scala")

  // Both the above effects will be processed in synchronized manner for e.g.
  // In a for-comprehension they are evaluated one after the another

  /*
    Fiber = Lightweight thread
     - It is description of a computation that will be performed by one of the Threads that is managed by ZIO Runtime
   */

  def createFiber: Fiber[Throwable, String] = ??? // Usually impossibe to create manually

  private val aSequentialIO =
    for
      mol <- meaningOfLife.debugThread
      fav <- favLanguage.debugThread
    yield (mol, fav)

  private val aConcurrentIO =
    for
      mol <- meaningOfLife.debugThread.fork
      fav <- favLanguage.debugThread.fork
    yield (mol, fav)

  private val meaningOfLifeFiber: URIO[Any, Fiber.Runtime[Nothing, Int]] = meaningOfLife.fork

  // Other operaions on Fiber is join, this is similar to what we have in Threads. Where the Fiber.join means it wait before the given thread is completed.

  // j1. oin a fiber
  def runOnAnotherThread[R, E, A](zio: ZIO[R, E, A]) =
    for
      fib    <- zio.fork
      result <- fib.join
    yield result

  // 2. awaiting a fiber
  /*
    The difference from join is, in await, the response is wrapper inside a ExitCode data structure
   */
  def runOnAnotherThreadWithAwait[R, E, A](zio: ZIO[R, E, A]) =
    for
      fib    <- zio.fork
      result <- fib.await
    yield result

  // what we usually do is
  def runOnAnotherThreadWithAwait_v2[R, E, A](zio: ZIO[R, E, A]) =
    for
      fib    <- zio.fork
      result <- fib.await
    yield result match
      case Success(value) => s"Succeeded with $value"
      case Failure(cause) => s"Failed with $cause"

  // 3. poll - peek at the result of the fiber RIGHT NOW without blocking

  private val peekFiber: ZIO[Any, Nothing, Option[Exit[Throwable, Int]]] =
    for
      fib    <- ZIO.attempt {
                  Thread.sleep(42)
                  42
                }.fork
      result <- fib.poll
    yield result // the result will be mostly none because .poll returns Option[ExitCode[E, A]], none means the fiber is not yet completed.

  // Fibers can also be composed
  // 1. zip
  private val aZippedFibers: ZIO[Any, Nothing, (String, String)] =
    for
      fib1 <- ZIO.succeed("Result from Fiber 1").debugThread.fork
      fib2 <- ZIO.succeed("Result from Fiber 2").debugThread.fork
      zipFib = fib1.zip(fib2)
      tuple <- zipFib.join
    yield tuple

  // 2. orElse - when one fiber fails it executes else one
  val chainedFibers =
    for
      fib1 <- ZIO.fail("not good").debugThread.fork
      fib2 <- ZIO.succeed("rock the jvm").debugThread.fork
      orElse = fib1.orElse(fib2)
      result <- orElse.join
    yield result

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    runOnAnotherThread(meaningOfLife).debugThread *>
      runOnAnotherThreadWithAwait(meaningOfLife).debugThread *>
      runOnAnotherThreadWithAwait_v2(meaningOfLife).debugThread *>
      peekFiber.debugThread *>
      aZippedFibers.debugThread *>
      chainedFibers.debugThread
