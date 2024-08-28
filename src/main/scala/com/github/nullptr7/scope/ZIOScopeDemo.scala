package com.github.nullptr7.scope

import zio.*

import java.io.IOException
import scala.io.Source

object ZIOScopeDemo extends ZIOAppDefault:
  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    contents("sample_scope.txt")
      .flatMap(x => Console.printLine(x.mkString))

  private def acquire(name: => String): IO[IOException, Source] =
    ZIO.attemptBlockingIO(Source.fromFile(name))

  private def release(source: => Source): UIO[Unit] =
    ZIO.succeedBlocking(source.close())

  private def source(name: => String): ZIO[Any with Scope, IOException, Source] =
    ZIO.acquireRelease(acquire(name))(release(_))

  private def contents(name: => String): Task[Chunk[String]] =
    ZIO.scoped {
      source(name).flatMap(s => ZIO.attemptBlocking(Chunk.fromIterator(s.getLines())))
    }
