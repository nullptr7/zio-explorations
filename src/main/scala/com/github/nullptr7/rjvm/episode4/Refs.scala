package com.github.nullptr7
package rjvm
package episode4

import java.util.concurrent.TimeUnit
import zio.*

object Refs extends ZIOAppDefault:

  // Refs are purely function atomic references
  private val atomicMol: UIO[Ref[Int]] = Ref.make(42)

  // To obtain a value from ref
  private val mol: ZIO[Any, Nothing, Int] = atomicMol.flatMap { ref =>
    ref.get // returns a UIO[Int], thread-safe getter
  }

  // To change a value of the ref
  private val setMol: ZIO[Any, Nothing, Unit] = atomicMol.flatMap { ref =>
    ref.set(100) // returns a UIO[Unit], thread-safe setter
  }

  // To get + change in one atomic operation
  private val getSetMol = atomicMol.flatMap { ref =>
    ref.getAndSet(500) // will return the get first i.e. first then it will store 500
  }

  // Update run a function on the value
  private val updatedMol: ZIO[Any, Nothing, Unit] = atomicMol.flatMap { ref =>
    ref.update(_ * 100)
  }

  // update + get in ONE operation
  private val updatedMolWithValue = atomicMol.flatMap { ref =>
    ref.updateAndGet(_ * 100) // returns the NEW value
    ref.getAndUpdate(_ * 100) // returns the OLD value
  }

  /*
    In all the above scenarios we were having to deal with same datatype for e.g. Int in this case.
    However, in below API we can generate a different datatype which can be different from the we are working on
   */
  private val modifiedMol = atomicMol.flatMap { ref =>
    ref.modify(value => (s"My current value is $value", value * 100))
  }

  // example: distributing the work te count the words in the sentence.
  // BAD example below
  import utils.*

  private def demoConcurrentWorkImpure(): UIO[Unit] =
    var count = 0

    def task(workload: String): UIO[Unit] =
      val wordCount = workload.split(" ").length
      for {
        _        <- ZIO.succeed(s"Counting words for: $workload: $wordCount").debugThread
        newCount <- ZIO.succeed(count + wordCount)
        _        <- ZIO.succeed(s"new total: $newCount").debugThread
        _        <- ZIO.succeed(count += wordCount) // here we are updating the variable
      } yield ()

    val effects = List("Quick brown fox", "jumped over", "lazy dog two times").map(task)
    ZIO.collectAllParDiscard(effects)
    /*
      Drawbacks
      - NOT THREAD SAFE!
      - hard to debug incase of failures
      - mixing pure and impure code
     */

  private def demoConcurrentWorkPure(): UIO[Unit] =

    def task(workload: String, total: Ref[Int]): UIO[Unit] =
      val wordCount = workload.split(" ").length
      for {
        _        <- ZIO.succeed(s"Counting words for: $workload: $wordCount").debugThread
        newCount <- total.updateAndGet(_ + wordCount)
        _        <- ZIO.succeed(s"new total: $newCount").debugThread
      } yield ()

    val sentences = List("Quick brown fox", "jumped over", "lazy dog two times")

    for {
      counter <- Ref.make(0)
      _       <- ZIO.collectAllParDiscard(sentences.map(task(_, counter)))
    } yield ()

  /** Exercises
    */
  // 1
  private def tickingClockImpure(): UIO[Unit] =
    var ticks = 0L
    // print the current time every 1seconds + increase a counter ("ticks")
    def tickingClock: UIO[Unit] = for {
      _ <- ZIO.sleep(1.second)
      _ <- Clock.currentTime(TimeUnit.MILLISECONDS).debugThread
      _ <- ZIO.succeed(ticks += 1)
      _ <- tickingClock
    } yield ()

    // print the total ticks count every 5 seconds
    def printTicks: UIO[Unit] = for {
      _ <- ZIO.sleep(5.seconds)
      _ <- ZIO.succeed(s"TICKS: $ticks").debugThread
      _ <- printTicks
    } yield ()

    tickingClock.zipPar(printTicks).unit

  private def tickingClockPure(): UIO[Unit] =
    // print the current time every 1seconds + increase a counter ("ticks")
    def tickingClock(currentTick: Ref[Long]): UIO[Unit] = for {
      _ <- ZIO.sleep(1.second)
      _ <- Clock.currentTime(TimeUnit.MILLISECONDS).debugThread
      _ <- currentTick.update(_ + 1)
      _ <- tickingClock(currentTick)
    } yield ()

    // print the total ticks count every 5 seconds
    def printTicks(currentTick: Ref[Long]): UIO[Unit] = for {
      _         <- ZIO.sleep(5.seconds)
      tickCount <- currentTick.get
      _         <- ZIO.succeed(s"TICKS: $tickCount").debugThread
      _         <- printTicks(currentTick)
    } yield ()
    for {
      initialTick <- Ref.make(0L)
      _           <- tickingClock(initialTick).zipPar(printTicks(initialTick))
    } yield ()

  // 2 - This is for more of an explaination as to what is going to happen
  /*
    [ZScheduler-Worker-6] 1670881952626
    [ZScheduler-Worker-7] 1670881953648
    [ZScheduler-Worker-0] 1670881954659
    [ZScheduler-Worker-3] 1670881955666
    [ZScheduler-Worker-4] TICKS: 0
    [ZScheduler-Worker-5] 1670881956680
    [ZScheduler-Worker-2] 1670881957688
    [ZScheduler-Worker-1] 1670881958700
    [ZScheduler-Worker-6] 1670881959702
    [ZScheduler-Worker-7] 1670881960712
    [ZScheduler-Worker-0] TICKS: 0
    [ZScheduler-Worker-3] 1670881961715
    [ZScheduler-Worker-4] 1670881962729
    [ZScheduler-Worker-5] 1670881963734
    [ZScheduler-Worker-2] 1670881964741
    [ZScheduler-Worker-1] 1670881965758
    [ZScheduler-Worker-6] TICKS: 0

    Why this is zero???
    Answer -> the 'ticksRef' is an effect that on evaluation generates Ref[Long] hence in both the functions they are evaluated seperately
    Also, they are called recursively meaning that effect will be evaluated every time hence the ticket is always O
   */
  private def tickingClockPure_v2(): UIO[Unit] =
    val ticksRef = Ref.make(0L)
    // print the current time every 1seconds + increase a counter ("ticks")
    def tickingClock: UIO[Unit] = for {
      currentTick <- ticksRef
      _           <- ZIO.sleep(1.second)
      _           <- Clock.currentTime(TimeUnit.MILLISECONDS).debugThread
      _           <- currentTick.update(_ + 1)
      _           <- tickingClock
    } yield ()

    // print the total ticks count every 5 seconds
    def printTicks: UIO[Unit] = for {
      currentTick <- ticksRef
      _           <- ZIO.sleep(5.seconds)
      tickCount   <- currentTick.get
      _           <- ZIO.succeed(s"TICKS: $tickCount").debugThread
      _           <- printTicks
    } yield ()

    tickingClock.zipPar(printTicks)

  // update function may be run more than once
  /*
    Meaning, in the program below, the update/modify api is excecuted in parallel hence multiple fibers will try to update the ref.
    But at any given time on ony fiber is allowed to update. In that meantime the other fibers if they want to use update/modify API need to wait.
    Once their turn comes in, they make their update/modify.
   */
  private def demoMultipleUpdates: UIO[Unit] =

    def task(id: Int, ref: Ref[Int]): UIO[Unit] =
      ref.modify(previous => (println(s"Task $id updating ref at $previous"), id))

    for {
      ref <- Ref.make(0)
      _   <- ZIO.collectAllParDiscard((1 to 10).toList.map(i => task(i, ref)))
    } yield ()

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = /*getSetMol.map(println)*/
    /*demoConcurrentWorkImpure() *> demoConcurrentWorkPure()*/
    demoMultipleUpdates
