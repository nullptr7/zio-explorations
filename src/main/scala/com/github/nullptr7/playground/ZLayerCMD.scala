package com.github.nullptr7.playground

import zio.*

object ZLayerCMD extends ZIOAppDefault:
  sealed trait CMDReaderService:
    def read: UIO[Unit]

  case class CMDReaderServiceLive(args: ZIOAppArgs) extends CMDReaderService:
    override def read: UIO[Unit] =
      ZIO.succeed(println(s"Printing ${args.getArgs.mkString}"))

  object CMDReaderServiceLive:
    def live: ZLayer[ZIOAppArgs, Nothing, CMDReaderServiceLive] = ZLayer.fromFunction(CMDReaderServiceLive.apply)

  def readMe(): ZIO[CMDReaderService, Nothing, Unit] =
    for {
      readerService <- ZIO.service[CMDReaderService]
      _             <- readerService.read
    } yield ()

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    readMe()
      .provideLayer(
        ZLayer.succeed(getArgs) >>> CMDReaderServiceLive.live
      )
