package com.github.nullptr7
package routes

import zhttp.http.*
import zio.*
import zio.json.*

import models.*

object UserApp:
  def apply(): Http[Any, Throwable, Request, Response] =
    Http.collectZIO[Request] {

      case req @ Method.POST -> _ / "users" =>
        for
          u <- req
                 .body
                 .asString
                 .map(_.fromJson[User])
          r <- u match
                 case Left(e)  =>
                   ZIO
                     .debug(s"Failed to parse the input: $e")
                     .as(
                       Response.text(e).setStatus(Status.BadRequest)
                     )
                 case Right(u) =>
                   ZIO.succeed(Response.text(u.toString).setStatus(Status.BadRequest))
        yield r
    }
