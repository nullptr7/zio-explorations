package com.github.nullptr7
package rjvm
package episode3

import utils.*
import zio.*

object MasteringInterruptions extends ZIOAppDefault:
  // We can do interruptions in several ways
  /*
    fib.interrupt by calling .interrupt

    ZIO.race, ZIO.zipPar, ZIO.collectAllPar // if any of the fibers performing some op is interrupted entire .race, .zipPar, .collectAllPar is interrupted

    Interruptions also happens, when child outlives parents
   */

  // manual interruptions
  private val aManualInterruptedZIO =
    ZIO.succeed("computing...").debugThread *> ZIO.interrupt *> ZIO.succeed(42).debugThread

  // finalizer
  private val effectWithInterruptionFinalizer = aManualInterruptedZIO.onInterrupt(ZIO.succeed("I am interrupted").debugThread)

  // there may be cases, when you do not want the effect to be NOT interrupted
  // Let's make a payment flow, that we do not want to be interrupted
  private val fussyPaymentSystems = (
    ZIO.succeed("payment running, don't cancel me...").debugThread *>
      ZIO.sleep(1.second) *>                                                 // this does the actual payment step
      ZIO.succeed("payment completed").debugThread
  ).onInterrupt(ZIO.succeed("the payment call is interrupted!").debugThread) // we do not want this to be triggered

  // This is bad because this will definitely be interrupted
  private val cancellationEffPayment =
    for
      paymentFib <- fussyPaymentSystems.fork
      _          <- ZIO.sleep(500.millis) *> ZIO.succeed("interrupting...").debugThread *> paymentFib.interrupt
      _          <- paymentFib.join
    yield ()

  // we can use .uninterruptible API to not interrupted a given effect
  // This makes the ZIO effect atomic
  private val atomicPaymentSystem:    ZIO[Any, Nothing, String] = ZIO.uninterruptible(fussyPaymentSystems)
  private val atomicPaymentSystem_v2: ZIO[Any, Nothing, String] = fussyPaymentSystems.uninterruptible

  private val noCancellationEffPayment =
    for
      paymentFib <- atomicPaymentSystem.fork
      _          <- ZIO.sleep(500.millis) *> ZIO.succeed("interrupting...").debugThread *> paymentFib.interrupt
      _          <- paymentFib.join
    yield ()

  // Interruptibility is a regional setting. Meaning we can apply interruptibility to certain effects
  private val zio1        = ZIO.succeed(1)
  private val zio2        = ZIO.succeed(2)
  private val zio3        = ZIO.succeed(3)
  private val composedZIO = (zio1 *> zio2 *> zio3).uninterruptible // In this case all the three effects are uninterruptible

  // In the case below interruptible is enclosed with uninterruptible. However, in this case, inner scope overrides
  // zio1 and zio3 are uninterruptible whereas zio2 is interruptible
  private val composedZIO_v2 = (zio1 *> zio2.interruptible *> zio3).uninterruptible

  // The above apis are rare, because ZIO provides a powerful way to deal with interruptible and uninterruptible effects
  // uninterruptibleMask

  /*
    example: Authentication Service
     - input password, can be interrupted, because otherwise it might block the fibers functionality
     - verify password, which cannot be interrupted once it's triggered
   */
  private val inputPassword =
    for
      _    <- ZIO.succeed("Input password...").debugThread
      _    <- ZIO.succeed("(typing password)").debugThread
      _    <- ZIO.sleep(5.seconds)
      pass <- ZIO.succeed("Foo@123!")
    yield pass

  private def verifyPassword(pw: String) =
    for
      _      <- ZIO.succeed("verifying...").debugThread
      _      <- ZIO.sleep(2.seconds)
      result <- ZIO.succeed(pw == "Foo@123!")
    yield result

  /** By default everything inside ZIO.uninterruptibleMask is uninterruptible. However, there is an input 'restore' in this case,
    * the effect when enclosed within 'restore' will be interrupted
    */
  private val authFlow = ZIO.uninterruptibleMask { restore =>
    for
      pw          <- restore(inputPassword) /* only this thing is interrupted as it is inside restore */.onInterrupt(ZIO.succeed("authentication timed out, please try again later"))
      // ^^ restores the interruptibility flag of this ZIO at the time of the call
      verifcation <- verifyPassword(pw)
      _           <- if verifcation then ZIO.succeed("authentication success!").debugThread
                     else ZIO.succeed("authentication failed...").debugThread
    yield ()
  }

  private val authFlowNoInterruptions = ZIO.uninterruptibleMask { _ =>
    for
      pw          <- inputPassword /* onInterrupt will never be called */.onInterrupt(ZIO.succeed("authentication timed out, please try again later"))
      verifcation <- verifyPassword(pw)
      _           <- if verifcation then ZIO.succeed("authentication success!").debugThread
                     else ZIO.succeed("authentication failed...").debugThread
    yield ()
  }

  private val authProgram =
    for
      authFlowFib <- authFlow.fork
      _           <- ZIO.sleep(2.seconds) *> ZIO.succeed("attempting to cancel authentication").debugThread *> authFlowFib.interrupt
      _           <- authFlowFib.join
    yield ()

  private val authProgram_v2 =
    for
      authFlowFib <- authFlowNoInterruptions.fork
      _           <- ZIO.sleep(2.seconds) *> ZIO.succeed("attempting to cancel authentication").debugThread *> authFlowFib.interrupt
      _           <- authFlowFib.join
    yield ()

  /** Exercises
    */

  // what will the below two effects do?

  // Ans. This one is interrupted rightaway because ZIO.interrupt is the first thing evaluated.
  private val cancelBeforeMol = ZIO.interrupt *> ZIO.succeed(42).debugThread

  // Ans. Even though this is enclosed inside interruptible block, the inner block which is interrupt takes priority.
  // Hence this too will be interrupted right from the start
  private val unCancelBeforeMol = ZIO.uninterruptible(ZIO.interrupt *> ZIO.succeed(42).debugThread)

  // Exercise 2

  /* Ans.
    Uninterruptible calls are masks which suppress cancellations. Restorer opens "gaps" in the unterruptible region
    If you wrap an entire structure with another. .uninterruptible/uninterrputibleMask you'll cover those gaps too.
    For this example. the program will cover all uninterruptible gaps, so the interruption signals will be ignored
   */

  private val authProgram_v3 =
    for
      authFlowFib <- ZIO.uninterruptibleMask(_ => authFlow).fork
      _           <- ZIO.sleep(3.seconds) *> ZIO.succeed("attempting to cancel the authentication").debugThread *> authFlowFib.interrupt
      _           <- authFlowFib.join
    yield ()

  // Exercise 3
  private val threeStepProram = ZIO.uninterruptibleMask { restore =>
    val sequence = for
      _ <- restore(ZIO.succeed("interruptible").debugThread *> ZIO.sleep(1.second))
      _ <- ZIO.succeed("uninterruptible").debugThread *> ZIO.sleep(1.second)
      _ <- restore(ZIO.succeed("interruptible 2")).debugThread *> ZIO.sleep(1.second)
    yield ()

    for
      fib <- sequence.fork
      _   <- ZIO.sleep(1500.millis) *> ZIO.succeed("INTERRUPTING!").debugThread *> fib.interrupt
      _   <- fib.join
    yield ()
  }

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    /* cancellationEffPayment *> */ /* noCancellationEffPayment *> */ /* authProgram *> */ /* authProgram_v2 *> */
    /* unCancelBeforeMol *> */ /* authProgram_v3 *> */ threeStepProram
