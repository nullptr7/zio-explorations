package com.github.nullptr7.zlayer

import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.{ BasicHttpClientResponseHandler, CloseableHttpClient, HttpClients }
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import zio.*

object ZIOMockingLayer /*extends ZIOAppDefault*/:
  //private val combinedLayer = ZLayer.succeed("https://api.agify.io/?name=meelad") >>> UrlServiceLive.layer

  /*override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    ZIO
      .serviceWith[UrlService](_.callMe())
      .provideLayer(combinedLayer)
      .flatten
      .tap(str => Console.printLine(str))*/

  trait UrlService:
    def callMe(): Task[String]

  trait HttpClientModule:
    protected def client: CloseableHttpClient

    final def sendAndReceive[A](url: String, responseHandler: HttpClientResponseHandler[A]): Task[A] =
      ZIO.attempt(client.execute(new HttpGet(url), responseHandler))

  private case class UrlServiceLive(url: String) extends UrlService with HttpClientModule:
    override def client: CloseableHttpClient = HttpClients.createDefault()

    override def callMe(): Task[String] = sendAndReceive[String](url, new BasicHttpClientResponseHandler)

  private object UrlServiceLive:
    val layer: URLayer[String, UrlServiceLive] = ZLayer.fromFunction(UrlServiceLive.apply _)
