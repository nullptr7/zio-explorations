package com.github.nullptr7
package rjvm
package episode3

import zio.*
import utils.debugThread
import java.io.FileWriter
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object WordsCounterApp extends ZIOAppDefault:
  private case class Content(value: String)

  private class ContentGeneratorService:
    val generate: UIO[Content] =
      for {
        nWords <- Random.nextIntBounded(2001)
        chars = 'a' to 'z'
        contents <- ZIO.succeed(
                      Content(
                        (1 to nWords)
                          .map(_ =>
                            (1 to scala.util.Random.nextInt(10))
                              .map(_ => chars(scala.util.Random.nextInt(26)))
                              .mkString
                          )
                          .mkString(" ")
                      )
                    )
      } yield contents

  private object ContentGeneratorService:
    def create(): ContentGeneratorService = new ContentGeneratorService()

    val live: ZLayer[Any, Nothing, ContentGeneratorService] = ZLayer.succeed(create())

  private class FileGeneratorService(contentGeneratorService: ContentGeneratorService):
    def generate(path: String): UIO[Unit] =
      for {
        contents <- contentGeneratorService.generate
        _        <- ZIO.succeed {
                      val aFile  = new File(path)
                      val writer = new FileWriter(aFile)
                      writer.write(contents.value)
                      writer.flush()
                      writer.close()
                    }
      } yield ()

  private object FileGeneratorService:
    def create(contentGeneratorService: ContentGeneratorService): FileGeneratorService =
      new FileGeneratorService(contentGeneratorService)

    val live: ZLayer[ContentGeneratorService, Nothing, FileGeneratorService] = ZLayer.fromFunction(create)

  private class WordCountService(n: Int):
    private def countWords(path: String): UIO[Int] =
      ZIO.succeed {
        val source = scala.io.Source.fromFile(path)
        val nWords = source.getLines().mkString(" ").split(" ").count(_.nonEmpty)
        source.close()
        nWords
      }

    val process: UIO[Int] =
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

  private object WordCountService:
    private def create(n: Int) = new WordCountService(n)

    def live(n: Int): ZLayer[Any, Nothing, WordCountService] = ZLayer.succeed(create(n))

  private object FileDeleteService:
    def cleanUp(): IO[Throwable, Unit] =
      ZIO
        .attempt {
          val f = new File("src/main/resources")
          f.listFiles().foreach(_.delete())
        }

  private val a = ContentGeneratorService.live >>> FileGeneratorService.live

  private val b = a ++ WordCountService.live(10)

  /* private val randomContentGenerator: UIO[String] =
    for {
      nWords <- Random.nextIntBounded(2001)
      chars = 'a' to 'z'
      contents <- ZIO.succeed(
                    (1 to nWords)
                      .map(_ =>
                        (1 to scala.util.Random.nextInt(10))
                          .map(_ => chars(scala.util.Random.nextInt(26)))
                          .mkString
                      )
                      .mkString(" ")
                  )

    } yield contents

  private val randomFileGenerator: UIO[Unit] = ??? */

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    val genProgram =
      ZIO
        .loop(0)(_ <= 10, _ + 1)(co =>
          for {
            x <- ZIO.service[FileGeneratorService]
            _ <- x.generate(s"src/main/resources/test_${co}.txt")
          } yield ()
        )
        .map(_ => ())

    val program = for {
      _     <- FileDeleteService.cleanUp()
      _     <- genProgram
      y     <- ZIO.service[WordCountService]
      count <- y.process
      _     <- Console.printLine(s"Total Number of Words - ${count}")
      _     <- FileDeleteService.cleanUp()
    } yield ()

    program.provideLayer(
      b
    )
