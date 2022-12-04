package com.github.nullptr7
package rjvm
package episode3

import zio.*
import utils.*
import java.util.concurrent.atomic.AtomicBoolean

object Blocking extends ZIOAppDefault:
  private def blockingTask(n: Int): UIO[Unit] =
    ZIO.succeed(s"Running blocking task $n").debugThread *>
      ZIO.succeed(Thread.sleep(10000)) *>
      blockingTask(n)

  // This program will block after sometime, but
  // Non blocking tasks will not have to regular threads in ZIO threadpool because all threads are in waiting state

  // This term is known as Thread Starvation
  private val program = ZIO.foreachPar((1 to 100).toList)(blockingTask) // All the threads managed by ZIO runtime will be blocked because of this operation

  /* Option no. 1
   */

  // Ideally we should run blocking calls under zio blocking thread pool i.e. delegate to ZIO blocking thread pool
  // Below ZIO effect will run on seperate thread pool mostly it will be 'zio-default-blocking-x' so that the regular thread pool is not blocked
  private val aBlockingZIO = ZIO.attemptBlocking {
    println(s"[${Thread.currentThread().getName()}] running long computation...")
    Thread.sleep(4000)
    42
  }

  // The issue above is, blocking code cannot usually be interrupted for e.g.
  /*
    In this scenario, the blocking is already sent however it doesn't interrupt immediatedly, that means in the aBlockingZIO
    Thread.sleep(4000) this is totally blocked for 4seconds and only after that we get the interruption message
   */
  private val aTryInterrupting =
    for
      blockingFib <- aBlockingZIO.fork
      _           <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> blockingFib.interrupt
      mol         <- blockingFib.join
    yield mol

  /** Option no. 2
    * we can use attemptBlockingInterrupt
    * This is based on java's Thread.interrupt API. This solution too may not work all the time. Specially when,
    * if the code which runnings inside attemptBlockingInterrupt has a try catch and this catch block catches 'InterruptedException'
    * This can be an issue, because when we interrupt, the ZIO will signal that thread to throw 'InterruptedException' and if our code had
    * catching mechanism then this catch will be served and we will not be able to interrupt successfully.
    */
  private val aBlockingInterruptibleZIO = ZIO.attemptBlockingInterrupt {
    println(s"[${Thread.currentThread().getName()}] running long computation...")
    Thread.sleep(4000)
    42
  }

  /** Option 3: The best way to do
    * Set some form of flag or switch inside of the computation
    * How it works..
    * When the ZIO 'effect' is interrupted i.e. when ZIO Runtime intersepts the interrupting signal to the fiber that executes that operation,
    * instead of interrupting the actual thread that is running the fiber that evaluates this ZIO,
    * the ZIO runtime simply runs the cancelled effect(second argument in attemptBlockingCancelable)
    * And in our case, this changes the atomicBoolean reference to false and thereby breaking out of the blocking operation
    */
  private def interruptibleBlockingEffect(cancelledFlag: AtomicBoolean): Task[Unit] =
    ZIO.attemptBlockingCancelable { // 'effect'
      (1 to 100000).foreach { element =>
        if !cancelledFlag.get() then
          println(s"Element $element on thread ${Thread.currentThread().getName()}");
          Thread.sleep(100)
      }
    }(
      ZIO.succeed(cancelledFlag.set(true))
    ) // cancelling/interrupting effect

  private val interruptibleDemo =
    for
      interruptFib <- interruptibleBlockingEffect(new AtomicBoolean(false)).fork
      _            <- ZIO.sleep(2.seconds) *> ZIO.succeed("interrupting...").debugThread *> interruptFib.interrupt
      _            <- interruptFib.join
    yield ()

  /** SEMANTIC Blocking
    * unlike normal blocking, this does not involves any blocked threads.
    * Scmantic blocking is done by de-scheduling the effect/fiber of that effect
    *
    * YIELDing:
    *        This is another signal that we can send to ZIO Runtime as a hint to de-schedule a fiber
    * Below two examples, they are not the same. As in 'zioSleep' this is semantic blocking because this is managed by ZIO Runtime.
    * Because ZIO.sleep(...) is just a data structure that will tell the ZIO Runtime to wait for x seconds and then notify the fiber when that x second is elapsed
    * So in this case, ZIO.runtime will yield control of the thread that is evaluating that effect and after x second that thread or some other thread
    * may be scheduled to run further effects
    * Whereas the 'nonSemanticSleep' we are explicitly doing Thread.sleep meaning a JVM managed thread is sleeping.
    */
  private val zioSleep         = ZIO.sleep(1.second)             // SEMANTICALLY blocking and fully interruptible, because
  private val nonSemanticSleep = ZIO.succeed(Thread.sleep(1000)) // completely blocking and uninterruptible

  // In ZIO.sleep the yield is done automatic however, we can also do manually as below
  // when we run this, all the effects are executed by one worker thread only.
  private val chainedZIO = (1 to 100).map(i => ZIO.succeed(i)).reduce(_.debugThread *> _.debugThread)

  // In this case, due to yieldNow so at some point the some other worker thread will come in and execute the remaining effects
  /*
  Note: yieldNow is just a HINT meaning it is upto ZIO Runtime that it can run some effects on a different worker thread.
    And runtime is smart enough to do what is best and pack several effects to same worker thread before yielding it
    Now, in most of the cases when the runtime encounters sees ZIO.sleep(x) this always yields control to other worker thread.
    Because sleeping for any number of time is still costlier operation than yielding and spinning up other worker threads i.e. Thread rescheduling overhead
  */
  private val chainedZIO_v2 = (1 to 100).map(i => ZIO.succeed(i)).reduce(_.debugThread *> ZIO.yieldNow *> _.debugThread)

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    /* interruptibleDemo *> */ chainedZIO_v2
