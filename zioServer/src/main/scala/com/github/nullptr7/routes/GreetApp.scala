package com.github.nullptr7
package routes

import zhttp.http.*
import zio.*

object GreetApp:
  def apply(): Http[Ref[String], Nothing, Request, Response] =
    Http.fromZIO(ZIO.service[Ref[String]]).flatMap { ref =>
      Http.collectZIO[Request] {
        case Method.GET -> !! / "up1"   =>
          ref
            .updateAndGet("Hello" + _)
            .map(Response.text)
        case Method.GET -> !! / "down1" =>
          ref
            .updateAndGet("Not Hello" + _)
            .map(Response.text)
        case Method.GET -> !! / "get1"  =>
          ref.get.map(Response.text)
      }
    }

