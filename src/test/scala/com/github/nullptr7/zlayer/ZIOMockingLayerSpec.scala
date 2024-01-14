package com.github.nullptr7.zlayer

import com.github.nullptr7.zlayer.ZIOMockingLayer.{ HttpClientModule, UrlService }
import com.github.nullptr7.zlayer.ZIOMockingLayerSpec.fixtures.UrlServiceMockLayer
import org.apache.hc.client5.http.impl.classic.{ CloseableHttpClient, HttpClients }
import zio.*
import zio.test.*
import zio.test.Assertion.*

object ZIOMockingLayerSpec extends ZIOSpecDefault:
  object fixtures:
    case class UrlServiceFailMock(url: String) extends UrlService with HttpClientModule:
      override def callMe(): Task[String] = ZIO.fail(new RuntimeException("This service call failed"))

      override protected def client: CloseableHttpClient = HttpClients.createMinimal()

    case class UrlServicePassMock(url: String) extends UrlService with HttpClientModule:
      override def callMe(): Task[String] =
        ZIO.succeed("pong!")

      override protected def client: CloseableHttpClient = HttpClients.createMinimal()

    object UrlServiceMockLayer:
      val failMockLayer: URLayer[String, UrlServiceFailMock] = ZLayer.fromFunction(UrlServiceFailMock.apply _)
      val passMockLayer: URLayer[String, UrlServicePassMock] = ZLayer.fromFunction(UrlServicePassMock.apply _)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("This suite passes")(
      test("passes obviously") {
        for {
          a <- ZIO.serviceWith[UrlService](_.callMe()).flatten
        } yield assertTrue(a == "pong!")
      }.provideLayer(ZLayer.succeed("http://fake-url.com") >>> UrlServiceMockLayer.passMockLayer),
      test("fails obviously") {
        val effect = ZIO.serviceWith[UrlService](_.callMe()).flatten
        assertZIO(effect.exit)(Assertion.fails(isSubtype[RuntimeException](anything)))
      }.provideLayer(ZLayer.succeed("http://fake-url.com") >>> UrlServiceMockLayer.failMockLayer),
    )
