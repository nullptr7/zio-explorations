package com.github.nullptr7
package rjvm
package episode2

import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.math.BigInt.apply

object ZIOEffects extends App:
  // We are creating simplified ZIO type
  private case class MyIO[-R, +E, +A](unsafeRun: R => Either[E, A]):
    def map[B](f: A => B): MyIO[R, E, B] =
      MyIO(r =>
        unsafeRun(r) match
          case Left(e)  => Left(e)
          case Right(a) => Right(f(a))
      )

    def flatMap[R1 <: R, E1 >: E, B](f: A => MyIO[R1, E1, B]): MyIO[R1, E1, B] =
      MyIO(r =>
        unsafeRun(r) match
          case Left(e)  => Left(e)
          case Right(a) => f(a).unsafeRun(r)
      )

  import zio.*

  // success
  val meaningOfLine: ZIO[Any, Nothing, Int] = ZIO.succeed(42)

  // failure
  val aFailure: ZIO[Any, String, Nothing] = ZIO.fail("something went wrong")

  // suspended/delay
  val aSuspendedOp: ZIO[Any, Throwable, Int] = ZIO.suspend(meaningOfLine)

  // This also has map and flatMap
  val upgradedMeaningOfLife: ZIO[Any, Nothing, Int]  = meaningOfLine.map(_ * 2)
  val printingMeaningOfLife: ZIO[Any, Nothing, Unit] = meaningOfLine.flatMap(mol => ZIO.succeed(println(mol)))

  // It also has forcomprehensions
  val smallProgram =
    for
      _    <- ZIO.succeed(println("Enter name"))
      name <- ZIO.succeed(scala.io.StdIn.readLine())
      _    <- ZIO.succeed(println(s"Hello, $name"))
    yield ()

  // It also has lot of combinators, the most common one is zip

  val zippedMOL:   ZIO[Any, Nothing, (Int, Int)] = meaningOfLine.zip(upgradedMeaningOfLife)
  val combinedZIO: ZIO[Any, Nothing, Int]        = meaningOfLine.zipWith(upgradedMeaningOfLife)(_ + _)

  /*
    Type aliases in ZIO
   */

  // UIO[A] universal IO that never fails = ZIO[Any, Nothing, A]
  // UIO[A] - ZIO[Any, Nothing, A] - no requirements, cannot fail and produces A
  val aUIO: UIO[Int] = ZIO.succeed(99)

  // Usually less used
  // URIO[R, A] - ZIO[R, Nothing, A] - have some requirements, cannot fail, produces A
  val aURIO: URIO[Int, Int] = ZIO.succeed(53)

  // RIO[R, A] - ZIO[R, Throwable, A] - have some requirements, can fail with throwable, produces A
  val anRIO:      RIO[Int, Int] = ZIO.succeed(324)
  val aFailedRIO: RIO[Int, Int] = ZIO.fail(new RuntimeException("Boom"))

  // Task[A] - ZIO[Any, Throwable, A] - no requirements, can fail with throwable, produces A
  val successfulTask: Task[Int] = ZIO.succeed(99)
  val aFailedTask:    Task[Int] = ZIO.fail(new RuntimeException("Boom"))

  // IO[E, A] - ZIO[Any, E, A] - no requirements, can fail with E, produces A
  val anIO:      IO[String, Int] = ZIO.succeed(42)
  val aFailedIO: IO[String, Int] = ZIO.fail("Failed IO")

  /** Exercises
    */

  // 1. Sequence two ZIOs and take the value of the last one
  def sequenceTakeLast[R, E, A, B](zioA: ZIO[R, E, A], zioB: ZIO[R, E, B]): ZIO[R, E, B] =
    zioA.flatMap(_ => zioB)
    // zioA *> zioB

  // 2. sequnce two ZIOs and take the value of the first one
  def sequenceTakeFirst[R, E, A, B](zioA: ZIO[R, E, A], zioB: ZIO[R, E, B]): ZIO[R, E, A] =
    zioA.zipLeft(zioB)
    // zioA.flatMap(a => zioB.map(_ => a))
    // zioA <* zioB

  // 3. Run a ZIO forever
  def runForever[R, E, A](zioA: ZIO[R, E, A]): ZIO[R, E, A] =
    zioA.flatMap(_ => runForever(zioA))
    // zioA *> runForever(zio)

  val endlessLoop = runForever {
    ZIO.succeed {
      println("Loading...")
      Thread.sleep(1000)
    }
  }

  // 4. Convert value of ZIO to something else.
  def convert[R, E, A, B](zioA: ZIO[R, E, A])(b: B): ZIO[R, E, B] =
    zioA.map(_ => b)
    // zioA.as(b)

  // 5. Discard a value of ZIO to unit
  def asUnit[R, E, A](zioA: ZIO[R, E, A]): ZIO[R, E, Unit] =
    zioA.map(println(_))
    // convert(zioA, ())
    // zioA.unit

  // 6. Recursion
  private def sum(n: Int): Int =
    if n == 0 then 0
    else n + sum(n - 1) // will crash at sum(20000)

  private def sumZIO(n: Int): UIO[Int] =
    if n == 0 then ZIO.succeed(0)
    else
      for {
        current <- ZIO.succeed(n)
        _       <- ZIO.succeed(println(n))
        prevSum <- sumZIO(n - 1)
      } yield current + prevSum

  object ImplicitConversion:
    given Conversion[Int, Second] = Second(_)

  case class Second(value: Int)

  object TimeUtil:
    def doSomethingWithProcessingTime(sec: Second): String =
      // impl logic
      s"${sec.value} seconds"

  object Usage:
    import ImplicitConversion.given
    private val processingTime = 100

    TimeUtil.doSomethingWithProcessingTime(processingTime)

  // 7. Fibonacci
  // hint: Use ZIO.suspend
  def fiboZIO(n: Int): UIO[Int] =

    @tailrec
    def calcuteFibo(
        nCounter: Int,
        counter1: Int,
        counter2: Int,
        sum:      Int,
      ): UIO[Int] =
      if nCounter == n then ZIO.succeed(sum)
      else calcuteFibo(nCounter + 1, counter2, counter1 + counter2, counter2 + sum)

    calcuteFibo(0, 0, 1, 0)

  private val runtime = Runtime.default
  given trace: Trace = Trace.empty

  def doSomething(f: Int => String): String = f(42)

  Unsafe.unsafe { implicit u =>
    val mol1 = runtime.unsafe.run(fiboZIO(4))
    println(mol1)
  }

  Unsafe.unsafe { i => 
    given Unsafe = i
    val mol1 = runtime.unsafe.run(fiboZIO(4))
    println(mol1)
  
  }