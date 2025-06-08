package com.github.nullptr7.http

import org.apache.hc.core5.http.message.BasicHttpRequest
import zio.*
import zio.http.*
import zio.test.*
import zio.ZIO.logInfo

import java.net.http.*
import java.net.*

object ZIOHttpTestSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment with Scope, Any] = ???

//  override def spec: Spec[TestEnvironment with Scope, Any] =
//    suite("this should work"):
//      test("another") {
//        for {
//          client <- ZIO.service[Client]
//          _      <- ZIO.logInfo("Starting server...")
//          fib    <- ZIOHttpTest.invoke(Chunk.empty).fork
//          _      <- ZIO.attempt(Thread.sleep(1000))
//
//          _        <- ZIO.logInfo("Server should be ready now")
//          response <- ZIO.attempt:
//                        HttpClient
//                          .newBuilder()
//                          .build()
//                          .send(HttpRequest.newBuilder(URI("http://localhost:9909/json")).GET().build(), HttpResponse.BodyHandlers.ofString())
//          //          response <- client.request(Request.get("http://localhost:9909/json"))
//          _        <- ZIO.logInfo(s"Received response: ${response.statusCode()}")
//                    _ <- fib.interrupt // Clean up server
//          _ <- ZIO.logInfo("interuptted")
//        } yield assertTrue(response.statusCode() == Status.Ok.code)
//      }.provide(
//        TestClient.layer
//          // Scope.default,
//      )
