package com.github.nullptr7
package prelude

object Zymposium extends App:
  import zio.prelude.*

  import zio.prelude.newtypes.*

  private object Name extends Subtype[String]
  private object Age extends Subtype[Int]
  private type Name = Name.Type
  private type Age  = Age.Type

  final private case class User(name: Name, age: Age)

  private def validateUsername(name: String): Validation[String, String] =
    if name.nonEmpty then Validation.succeed(name)
    else Validation.fail("Name is empty")

  private def validateAge(age: Int): Validation[String, Int] =
    if age < 5 then Validation.fail("Age is less than 5")
    else Validation.succeed(age)

  private def validateUser(name: String, age: Int): ZValidation[Nothing, String, User] =
    Validation.validateWith(
      validateUsername(name),
      validateAge(age),
    )((name, age) => User(Name(name), Age(age)))

  private val output = validateUser("ewwer", 31)
  println(output)
