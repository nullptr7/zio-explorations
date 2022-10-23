package com.github.nullptr7
package models

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

case class User(name: String, age: Int)

object User:
  given JsonCodec[User] = DeriveJsonCodec.gen[User]

  // implicit val userCodec: JsonCodec[User] = DeriveJsonCodec.gen[User]
