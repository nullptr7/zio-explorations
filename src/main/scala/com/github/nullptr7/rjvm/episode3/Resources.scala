package com.github.nullptr7
package rjvm
package episode3

import utils.*
import zio.*
import java.util.Scanner
import java.io.File

object Resources extends ZIOAppDefault:
  private def anUnsafeInt: Int = throw new RuntimeException("Bad method")

  // finalizers
  private val anAttemptWithFinalizer = ZIO.attempt(anUnsafeInt).ensuring(ZIO.succeed("Finalizer 1").debugThread)

  // Adding another finalizers
  private val anAttemptWithAnotherFinalizer = anAttemptWithFinalizer.ensuring(ZIO.succeed("Finalizer 2").debugThread)

  // similarly we have finalizers for
  // .onInterrupt, .onError, .onDone & .onExit
  // When do we usually use finalizers..? when we usually deal with resources and we want to free when we are executing the finalizer code.

  private class Connection(url: String):
    def open(): ZIO[Any, Nothing, String] = ZIO.succeed(s"Opening connection to $url").debugThread

    def close(): ZIO[Any, Nothing, String] = ZIO.succeed(s"Closing connection to $url").debugThread

  private object Connection:
    def create(url: String): ZIO[Any, Nothing, Connection] = ZIO.succeed(new Connection(url))

  private def fetchUrl: ZIO[Any, Nothing, Unit] =
    for
      conn <- Connection.create("https://github.com/nullptr7")
      fib  <- (conn.open() *> ZIO.sleep(300.seconds)).ensuring(conn.close()).fork
      _    <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
      _    <- fib.join
    yield () // this does not leak resources as we are effectively closing it. However the above is tedius as this has a lot of overhead of managing

  // Lesser tedious approach is below
  /*
  acquireRelease
    - acquiring cannot be interrupted
    - all finalizers are guaranteed to run
   */
  private val cleanConnection: ZIO[Scope, Nothing, Connection] = ZIO.acquireRelease(Connection.create("https://github.com/nullptr7"))(_.close())

  private val fetchWithResource =
    for
      conn <- cleanConnection
      fib  <- (conn.open() *> ZIO.sleep(300.seconds)).fork
      _    <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
      _    <- fib.join
    yield ()

  // acquireReleaseWith
  private val cleanConnection_v2 =
    ZIO.acquireReleaseWith(
      Connection.create("https://github.com/nullptr7") // acquire
    )(
      _.close()                                        // release
    )(conn => conn.open() *> ZIO.sleep(300.seconds)) // use

  private val fetchWithResource_v2 =
    for
      fib <- cleanConnection_v2.fork
      _   <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    yield ()

  /**  Exercise.
    *  1. Use the acquireRelease to open a file and print all lines(one every 100 millis), then close the file
    */
  private def openFileScanner(path: String): UIO[Scanner] =
    ZIO.succeed(new Scanner(new File(path)))

  private def acquireOpenFile(path: String) =
    for
      // scanner <- openFileScanner(path)
      lines <- ZIO.acquireRelease(openFileScanner(path))(s => ZIO.succeed("Closing the scanner").debugThread *> ZIO.succeed(s.close()))
      _     <- readLineByLine(lines)
    yield ()

  private def readLineByLine(lines: Scanner): UIO[Unit] =
    if lines.hasNextLine then ZIO.succeed(lines.nextLine()).debugThread *> ZIO.sleep(100.millis) *> readLineByLine(lines)
    else ZIO.unit

  private def acquireOpenFile_v2(path: String) =
    ZIO.succeed(s"Opening file at $path").debugThread *>
      ZIO.acquireReleaseWith(
        openFileScanner(path)
      )(scanner => ZIO.succeed(s"Closing file at $path").debugThread *> ZIO.succeed(scanner.close()))(readLines)

  private def readLines(scanner: Scanner): UIO[Unit] =
    if scanner.hasNextLine then ZIO.succeed(scanner.nextLine()).debugThread *> ZIO.sleep(100.millis) *> readLines(scanner)
    else ZIO.unit

  private val testInterruptFileDisplay =
    for
      fib <- acquireOpenFile("src/main/scala/com/github/nullptr7/rjvm/episode3/Resources.scala").fork
      _   <- ZIO.sleep(2.seconds) *> fib.interrupt
    yield ()

  private val testInterruptFileDisplay_v2 =
    for
      fib <- acquireOpenFile("src/main/scala/com/github/nullptr7/rjvm/episode3/Resources.scala").fork
      _   <- ZIO.sleep(2.seconds) *> fib.interrupt
    yield ()

  // acquireRelease vs acquireReleaseWith

  // Below is very tedious and difficult to debug when used with acquireReleaseWith
  private def connFromConfig(path: String): UIO[Unit] =
    ZIO.acquireReleaseWith(openFileScanner(path))(scanner => ZIO.succeed("closing file").debugThread *> ZIO.succeed(scanner.close())) { scanner =>
      ZIO.acquireReleaseWith(Connection.create(scanner.nextLine()))(_.close()) { conn =>
        conn.open() *> ZIO.never
      }
    }

  // Below one is better as we can write series of flatMap using for-comprehension
  private def connFromConfig_v2(path: String): ZIO[Scope, Nothing, Unit] =
    for
      scanner <- ZIO.acquireRelease(openFileScanner(path))(scanner => ZIO.succeed("closing file").debugThread *> ZIO.succeed(scanner.close()))
      conn    <- ZIO.acquireRelease(Connection.create(scanner.nextLine()))(_.close())
      _       <- conn.open() *> ZIO.never
    yield ()

  // override def run: ZIO[Any, Any, Any] = ZIO.scoped(testInterruptFileDisplay) /* *> fetchWithResource_v2 */ *> ZIO.scoped(testInterruptFileDisplay_v2)
  override def run: ZIO[Any, Any, Any] = ZIO.scoped(connFromConfig_v2("src/main/resources/connection.conf"))
