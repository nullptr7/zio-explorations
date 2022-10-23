package com.github.nullptr7
package entrypoint

import routes.*
import java.io.IOException
import zio.*
import zhttp.service.*

object Main extends ZIOAppDefault:
  def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    Server
      .start(
        port = 8080,
        http = CounterApp() ++ GreetApp() ++ UserApp(),
      )
      .provide(ZLayer.fromZIO(Ref.make(0)), ZLayer.fromZIO(Ref.make("Ishan")))

