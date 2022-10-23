package com.github.nullptr7
package configs

import zio.config.*
import ConfigDescriptor.*
import zio.*

object ConfigStart extends App:
  final case class ApplicationConfig(
      host:     String,
      port:     Int,
      username: String,
      password: String,
    )

  private val environment: Map[String, String] = Map(
    "HOSTNAME" -> "someHostName",
//    "PORT"     -> "8080",
//    "USERNAME" -> "username",
//    "PASSWORD" -> "password",
  )

  private val configDescriptor: ConfigDescriptor[ApplicationConfig] =
    (string("HOSTNAME") zip int("PORT") zip string("USERNAME") zip string("PASSWORD"))
      .to[ApplicationConfig]

  val reader: IO[ReadError[String], ApplicationConfig] = read(
    configDescriptor.from(ConfigSource.fromMap(environment))
  )

  val runtime = Runtime.default

  import Unsafe.unsafe

  

  Unsafe.unsafe { implicit unsafe =>
    println(runtime.unsafe.run(reader).getOrThrowFiberFailure())
  }

