package com.github.nullptr7
package rjvm
package episode3

import zio.*
import java.io.FileWriter
import java.io.File
import zio.Exit.Success
import zio.Exit.Failure
import utils.*

object FibersExercise extends ZIOAppDefault:

  /** Exercises
    * 1. Zip two fibers without using the 'zip' API
    */

  // 1. Zip two fibers without using the 'zip' API
  // Hint: create a fiber that waits for both of these fibers
  def zipFibers[E, A, B](fiber1: Fiber[E, A], fiber2: Fiber[E, B]): ZIO[Any, Nothing, Fiber[E, (A, B)]] =
    (for {
      f1 <- fiber1.join
      f2 <- fiber2.join
    } yield (f1, f2)).fork

  val zippedFibers_v2 =
    for
      fib1   <- ZIO.succeed("Result from fiber1").debugThread.fork
      fib2   <- ZIO.succeed("Result from fiber2").debugThread.fork
      fibers <- zipFibers(fib1, fib2)
      tuple  <- fibers.join
    yield tuple

  def zipFibers_v2[E, E1 <: E, E2 <: E, A, B](fiber1: Fiber[E1, A])(fiber2: Fiber[E2, B]): ZIO[Any, Nothing, Fiber[E, (A, B)]] =
    (for {
      f1 <- fiber1.join
      f2 <- fiber2.join
    } yield (f1, f2)).fork

  // 2. same thing with orElse
  def chainFibers[E, A](fiber1: Fiber[E, A], fiber2: Fiber[E, A]): ZIO[Any, Nothing, Fiber[E, A]] =
    val f1          = fiber1.join
    val f2          = fiber2.join
    val finalEffect = f1.orElse(f2) // here we are using orElse to ZIO effect not of fiber
    finalEffect.fork

  // 3. distributing task in between many fibers
  // spawn n fibers, count the n in each file,
  // then aggregate all the results together in one big number
  def generateRandomFile(path: String): Unit =
    val random   = scala.util.Random
    val chars    = 'a' to 'z'
    val nWords   = random.nextInt(2000) // we gonna generate atmost 2000 words
    val contents =
      (1 to nWords)
        .map(_ =>
          (1 to random.nextInt(10))
            .map(_ => chars(random.nextInt(26)))
            .mkString
        )
        .mkString(" ")
    val writer   = new FileWriter(new File(path))
    writer.write(contents)
    writer.flush()
    writer.close()

    // part 1 = an effect which reads one file and counts the words there.
    // one fiber for every one file...
    def countWords(path: String): UIO[Int] =
      ZIO.succeed {
        val source = scala.io.Source.fromFile(path)
        val nWords = source.getLines().mkString(" ").split(" ").count(_.nonEmpty)
        source.close()
        nWords
      }

    // part 2- Spin up fibers for all the files
    def wordCountParallel(n: Int): UIO[Int] =
      val effects: Seq[ZIO[Any, Nothing, Int]] =
        (1 to n)
          .map(i => s"src/main/resources/test_$i.txt")
          .map(countWords) // list of effects
          .map(_.fork) // list of effects returning fibers
          .map((fiberEff: ZIO[Any, Nothing, Fiber[Nothing, Int]]) => fiberEff.flatMap(_.join)) // list of effects returning values (count of words)

      effects.reduce { (zioa, ziob) =>
        for
          a <- zioa
          b <- ziob
        yield a + b
      }

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    /* ZIO.succeed(
      (1 to 10).foreach { counter =>
        generateRandomFile(s"src/main/resources/test_$counter.txt")
      }
    ) */
    ???
