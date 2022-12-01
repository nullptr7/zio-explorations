package com.github.nullptr7
package rjvm
package episode3

import zio.*
import utils.*

object Parallelism extends ZIOAppDefault:
  private val meaningOfLife = ZIO.succeed(42)
  private val favLang       = ZIO.succeed("Scala")
  private val combined      = meaningOfLife.zip(favLang) // combines and zips in a sequential manner

  // combine in parallel
  private val combinePar = meaningOfLife.zipPar(favLang) // combination in parallel

  // Things to consider when building zipPar
  /*
    1. start each on fibers
    2. what if one fails? the other should be interrupted
    3. what if one is interrupted? the entire thing should be interrupted
    4. what if the whole thing is interrupted? need to interrupt both effects
   */

  // try a zipPar combinator
  // hint: fork/join/await & interrupt

  /*
    Even though we have achieved some form of parallelism, however if we notice here, we are still waiting first ZIO to complete
    then we go ahead and inspect the second ZIO. This kind of leads with some race condition.
   */
  private def myZipPar[R, E, A, B](zioA: ZIO[R, E, A], zioB: ZIO[R, E, B]): ZIO[R, E, (A, B)] =

    val exits = for
      zioFibA <- zioA.fork
      zioFibB <- zioB.fork

      exitA <- zioFibA.await
      exitB <- exitA match
                 case Exit.Success(_) => zioFibB.await
                 case Exit.Failure(_) => zioFibB.interrupt
    yield (exitA, exitB)

    exits.flatMap {
      case (Exit.Success(a), Exit.Success(b))           => ZIO.succeed((a, b))
      case (Exit.Success(_), Exit.Failure(cause))       => ZIO.failCause(cause)
      case (Exit.Failure(cause), Exit.Success(_))       => ZIO.failCause(cause)
      case (Exit.Failure(cause1), Exit.Failure(cause2)) => ZIO.failCause(cause1 && cause2)
    }

  // more parallel combinators
  // zipPar, zipWithPar

  // collectAllPar
  private val effects:         Seq[ZIO[Any, Nothing, Int]] = (1 to 10).map(i => ZIO.succeed(i).debugThread)
  private val collectedValues: ZIO[Any, Nothing, Seq[Int]] = ZIO.collectAllPar(effects) // traverse

  // foreachPar
  private val printParallel: ZIO[Any, Nothing, List[Unit]] = ZIO.foreachPar((1 to 10).toList)(i => ZIO.succeed(println(i)))

  // reduceAllPar, mergeAllPar
  private val sumPar    = ZIO.reduceAllPar(ZIO.succeed(0), effects)(_ + _)
  private val sumPar_v2 = ZIO.mergeAllPar(effects)(0)(_ + _)

  /*
    general terminology of the par APIs
      - if all effects succeed, then we are all good
      - even if one effect fails => everyone is interrupted and the error cause is surfaced
      - if one effect is interrupted => everyone else is interrupted and the error (interruption) for the entire expression
      - if the entire thing is interrupted => all effects are interrupted
   */

  /** Exercise: do word counting exercise, using parallel combinator
    */

  private def countWords(path: String): UIO[Int] =
    ZIO.succeed {
      val source = scala.io.Source.fromFile(path)
      val nWords = source.getLines().mkString(" ").split(" ").count(_.nonEmpty)
      source.close()
      nWords
    }

  private val anEffectOfCountingWords =
    (1 to 10).map(count => countWords(s"src/main/resources/test_$count.txt"))

  private val finalCount    = ZIO.mergeAllPar(anEffectOfCountingWords)(0)(_ + _)
  private val finalCount_v2 = ZIO.collectAllPar(anEffectOfCountingWords).map(_.sum)
  private val finalCount_v3 = ZIO.reduceAllPar(ZIO.succeed(0), anEffectOfCountingWords)(_ + _)

  override def run: ZIO[Any, Any, Any] =
    // myZipPar(ZIO.succeed(42), ZIO.succeed("Scala")).debugThread

    /*printParallel.debugThread *> sumPar.debugThread *> sumPar_v2.debugThread *>*/
    finalCount.debugThread *> finalCount_v2.debugThread *> finalCount_v3.debugThread
