package com.github.nullptr7
package rjvm
package episode3

import utils.*
import zio.*

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
    yield () // this does not leak resources as we are effectively closing it.

  override def run: ZIO[Any, Any, Any] = fetchUrl
