package com.github.nullptr7
package rjvm
package episode4

import zio.*
import utils.*

object Promises extends ZIOAppDefault:

  // A promise similar to a ref is an effectful operation that returns a effect at the end
  private val aPromise: UIO[Promise[Throwable, Int]] = Promise.make[Throwable, Int]

  // Inorder to use this promise, we use can await
  private val aReader: ZIO[Any, Throwable, Int] = aPromise.flatMap(promise => ZIO.succeed("waiting...").debugThread *> promise.await)

  // In the above code, the reader will wait for that promise to succeed, which can be done by below
  // The promise can succeed, fail & complete
  private val aWriter: ZIO[Any, Nothing, Boolean] = aPromise.flatMap(promise => promise.succeed(42))

  private def demoPromise(): Task[Unit] =
    // producer - consumer problem
    def consumer(promise: Promise[Throwable, Int]): Task[Unit] =
      for
        _   <- ZIO.succeed("[consumer] waiting for result...").debugThread
        mol <- promise.await
        _   <- ZIO.succeed(s"[consumer] I got the result $mol").debugThread
      yield ()

    def producer(promise: Promise[Throwable, Int]): Task[Unit] =
      for
        _   <- ZIO.succeed("[producer] crunching numbers...").debugThread
        _   <- ZIO.sleep(3.seconds)
        _   <- ZIO.succeed(s"[producer] complete").debugThread
        mol <- ZIO.succeed(42)
        _   <- promise.succeed(mol)
      yield ()

    for
      promise <- Promise.make[Throwable, Int]
      _       <- consumer(promise).zipPar(producer(promise))
    yield ()

  /*
    - purely functional block on a fiber until you get a signal from another fiber
    - waiting on a value which may not yet be available, without thread starvation
    - inter-fiber communication
   */
  // Promise is a powerful super concurrency primitive, but it has different form of co-ordination as compared to Ref. For e.g.
  // Example: Simulation a downloading of a file with multiple parts.

  private val fileParts = List("I ", "love S", "cala", " with pure FP an", "d ZIO! <EOF>")

  private def downloadFileWithRef(): UIO[Unit] =
    def downloadFile(contentRef: Ref[String]): UIO[Unit] =
      ZIO.collectAllDiscard(
        fileParts.map { part =>
          ZIO.succeed(s"got '$part'").debugThread *> ZIO.sleep(1.second) *> contentRef.update(_ + part)
        }
      )

    def notifyFileCompletion(contentRef: Ref[String]): UIO[Unit] =
      for
        content <- contentRef.get
        _       <- if content.endsWith("<EOF>") then ZIO.succeed("File download complete").debugThread
                   else ZIO.succeed("downloading...").debugThread *> ZIO.sleep(500.millis) *> notifyFileCompletion(contentRef)
      yield ()

    for
      initialContent <- Ref.make("")
      _              <- downloadFile(initialContent).zipPar(notifyFileCompletion(initialContent))
    yield ()

  // if we run the above program, we would notice that the downloadFile keeps on printing as it is busy waiting for the download to be completed.

  // Another way of doing is with Ref & Promise
  private def downloadFileWithRefWithPromise(): Task[Unit] =
    def downloadFile(contentRef: Ref[String], promise: Promise[Throwable, String]): UIO[Unit] =
      ZIO.collectAllDiscard(
        // insert the stopping logic here
        fileParts.map { part =>
          for {
            _             <- ZIO.succeed(s"got '$part'").debugThread
            _             <- ZIO.sleep(1.second)
            latestContent <- contentRef.updateAndGet(_ + part)
            /*_             <- if latestContent.endsWith("<EOF>") then promise.succeed(latestContent)
                             else ZIO.unit*/
            _             <- promise.succeed(latestContent).when(latestContent.endsWith("<EOF>"))
          } yield ()
        }
      )

    def notifyFileCompletion(promise: Promise[Throwable, String]): Task[Unit] =
      for
        _    <- ZIO.succeed("downloading..").debugThread
        file <- promise.await
        _    <- ZIO.succeed(s"file download complete: $file").debugThread
      yield ()

    for
      initialContent <- Ref.make("")
      promise        <- Promise.make[Throwable, String]
      _              <- downloadFile(initialContent, promise).zipPar(notifyFileCompletion(promise))
    yield ()

  /** Exercises
    * 1. Write a simulated "egg boiler" with two ZIOs
    *    - one increment a counter every 1s
    *    - one waits for the counter to become 10, after which it will "ring a bell"
    *
    * 2. Write a "race pair"
    *  Function that takes two ZIOs, they will run on separate fibers. And the winner of the fiber will determine the outcome of the race.
    *   Hint:
    *    - use a Promise which can hold on Either[exit for A, exit for B]
    *    - start a fiber for each ZIO
    *    - on completion(with any status), each ZIO needs to complete that promise (use finalizer)
    *    - waiting on the promise's value can be interrupted!
    *    - if the whole race is interrupted, interrupt the running fiber
    */
  private def eggBoiler(): Task[Unit] =
    def incrementCount(counter: Ref[Int], promise: Promise[Throwable, Int]): Task[Unit] =
      for
        currentCount <- counter.updateAndGet(_ + 1)
        _            <- ZIO.succeed(s"$currentCount").debugThread
        _            <- if currentCount == 10 then promise.succeed(10) else incrementCount(counter, promise).delay(1.second)
      yield ()

    def notifyBell(promise: Promise[Throwable, Int]): Task[Unit] =
      for
        _ <- ZIO.succeed("Waiting 10 seconds").debugThread
        _ <- promise.await
        _ <- ZIO.succeed("Egg is ready!").debugThread
      yield ()

    for
      _       <- ZIO.succeed("Starting to make the egg").debugThread
      promise <- Promise.make[Throwable, Int]
      counter <- Ref.make(0)
      _       <- incrementCount(counter, promise).zipPar(notifyBell(promise))
    yield ()

  private def racePair[R, E, A, B](
      zioA: => ZIO[R, E, A],
      zioB: => ZIO[R, E, B],
    ): ZIO[R, Nothing, Either[(Exit[E, A], Fiber[E, B]), (Fiber[E, A], Exit[E, B])]] =
    ZIO.uninterruptibleMask { restore =>
      for
        promise <- Promise.make[Nothing, Either[Exit[E, A], Exit[E, B]]]
        aFib    <- zioA.onExit(exitA => promise.succeed(Left(exitA))).fork
        bFib    <- zioB.onExit(exitB => promise.succeed(Right(exitB))).fork
        result  <- restore(promise.await).onInterrupt {
                     for
                       interruptA <- aFib.interrupt.fork
                       interruptB <- bFib.interrupt.fork
                       _          <- interruptA.join
                       _          <- interruptB.join
                     yield ()
                   }
      yield result match
        case Left(exitA)  => Left((exitA, bFib))
        case Right(exitB) => Right(aFib, exitB)
    }

  private val demoRacePair: ZIO[Any, Nothing, Exit[Nothing, RuntimeFlags]] =
    val zioA = ZIO.sleep(2.second).as(1).onInterrupt(ZIO.succeed("first interrupted").debugThread)
    val zioB = ZIO.sleep(1.second).as(2).onInterrupt(ZIO.succeed("second interrupted").debugThread)

    val pair = racePair(zioA, zioB)

    pair.flatMap {
      case Left((exitA, fibB))  => fibB.interrupt *> ZIO.succeed("first won").debugThread *> ZIO.succeed(exitA).debugThread
      case Right((fibA, exitB)) => fibA.interrupt *> ZIO.succeed("second won").debugThread *> ZIO.succeed(exitB).debugThread
    }

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    /*demoPromise()*/ /*downloadFileWithRef()*/ /*eggBoiler()*/ demoRacePair
