package com.github.nullptr7
package playground

import zio.prelude.*
import zio.prelude.newtypes.*
object PreludeDemo extends App:
  final case class Person private (
      firstname: String,
      lastname:  String,
      age:       Int,
    )

  object Person:
    def make(
        firstname: String,
        lastname:  String,
        age:       Int,
      ): Validation[String, Person] =
      Validation.validateWith(
        validateFirstname(firstname),
        validateLastname(lastname),
        validateAge(age),
      )(Person.apply)

    private def validateFirstname(firstname: String): Validation[String, String] =
      if firstname.nonEmpty then Validation.succeed(firstname)
      else Validation.fail("First name should not be empty")

    private def validateLastname(lastname: String): Validation[String, String] =
      if lastname.nonEmpty then Validation.succeed(lastname)
      else Validation.fail("Last name should not be empty")

    private def validateAge(age: Int): Validation[String, Int] =
      if age > 5 then Validation.succeed(age)
      else Validation.fail("Age cannot be less than 5 years")

  println(Person.make("123", "123", 10))

  given associativity: Associative[Map[String, Int]] = new Associative[Map[String, Int]]:
    override def combine(l: => Map[String, Int], r: => Map[String, Int]): Map[String, Int] = ???

  final case class VoteState(map: Map[String, Int]) { self =>
    def combine(that: VoteState): VoteState =
      VoteState(Sum.wrapAll(self.map) <> Sum.wrapAll(that.map))

  }
