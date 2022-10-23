package com.github.nullptr7
package routes

import zhttp.http.*
import zio.*

object CounterApp:
  def apply(): Http[Ref[Int], Nothing, Request, Response] =
    Http.fromZIO(ZIO.service[Ref[Int]]).flatMap { ref =>
      Http.collectZIO[Request] {
        case Method.GET -> _ / "up"   =>
          ref
            .updateAndGet(_ + 1)
            .map(_.toString)
            .map(Response.text)
        case Method.GET -> _ / "down" =>
          ref
            .updateAndGet(_ - 1)
            .map(_.toString)
            .map(Response.text)
        case Method.GET -> _ / "get"  =>
          ref.get.map(_.toString).map(Response.text)
      }
    }

