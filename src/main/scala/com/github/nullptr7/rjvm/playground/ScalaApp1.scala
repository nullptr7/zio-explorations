package com.github.nullptr7
package rjvm
package playground

object ScalaApp1 extends App:
  private trait Combine[A]:
    def combine(x: A, y: A): A
    def empty: A

  private def combineAll[A](list: List[A])(using combine: Combine[A]): A =
    list.foldLeft(combine.empty)(combine.combine)

  private given intCombiner: Combine[Int] with
    override def combine(x: Int, y: Int): Int = x + y

    override def empty: Int = 0

  val numbers: List[Int] = (1 to 10).toList

  val sum10: Int = combineAll(numbers)

  println(sum10)

  // synthesize instances
  private given optionCombiner[T](using combine: Combine[T]): Combine[Option[T]] with
    override def combine(x: Option[T], y: Option[T]): Option[T] =
      for
        vx <- x
        vy <- y
      yield combine.combine(vx, vy)

    override def empty: Option[T] = Some(combine.empty)

  private val optionNumbers: List[Option[Int]] = List(Some(1), None, Some(5))

  private val sumOpt = combineAll(optionNumbers)

  println(sumOpt)

  // extension method

  private case class Person(name: String):
    def greet: String = s"Hi, my name is $name"

  extension (name: String) private def greet(): String = Person(name).greet

  println("Ishan".greet())

  // generic extension
  extension [T](list: List[T])
    private def reduceAll(using combine: Combine[T]): T =
      list.foldLeft(combine.empty)(combine.combine)

  println(List(1, 2, 34, 5).reduceAll)
