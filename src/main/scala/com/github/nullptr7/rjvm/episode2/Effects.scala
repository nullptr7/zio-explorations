package com.github.nullptr7.rjvm.episode2

import scala.concurrent.Future

object Effects extends App:

  // functional programming
  // We deal with expressions of a program.
  def combine(a: Int, b: Int): Int = a + b

  // Core concepts
  // local reasoning = type signature describes the type of computation it will performs
  // referential transparency = ability to replace an expression with the value that it evaluates to

  val five    = combine(2, 3)
  val five_v2 = 2 + 3
  val five_v3 = 5

  // not all expressions are referentially transparent

  // example 1: Printing on the console
  val printingSomething:         Unit = println("printing")
  val notPrintingButSendingUnit: Unit = () // not referentially transparent

  // example 2: changing a variable
  var anInt:         Int  = 0
  val changingInt:   Unit = anInt = 42 // side effect
  val changingIntV2: Unit = () // not the same program

  // Side effects are inevitable

  /*
    Effects
    - the type signature should describe what KIND of computation it will perform
    - the type signature describes what kind of VALUE it will produce
    - if side effects is required, construction must be separate from the EXECUTION
   */

  /*
    Example: Option = possibly absent values
    If we check the effect steps
      - type signature DOES describe the kind of computation i.e. a possibly an absent value
      - the type signature DOES describe the value it will produce A if the computation produce something
      - Since the computation is already done and wrapped in option it means no side effects needed. Hence there is
          no debate if the construction is separate from execution

    Hence, option is an EFFECT!
   */
  val anOption: Option[Int] = Option(42)

  /*
    Example: Future = async computation that maybe finished in the future
    If we check the effect steps
      - type signature DOES describe the kind of computation i.e. this future may generate a value int in some future time
      - type signature does describe the value it will produce. Which is an INT if the future is success
      - Side effect is possible for e.g. Future(print(42)).
          The effect is already loaded and scheduled for execution the moment it is constructed. Meaning there is no delay in execution of the effect.
          Hence, construction is not separate from execution. Hence this is NOT an effect!
   */
  import scala.concurrent.ExecutionContext.Implicits.global
  val aFuture: Future[Int] = Future(42)

  /*
    Example: MyIO = similar to what we have in other FP libs like IO[+A]
    If we check the effect steps
      - type signature DOES describe the type of computation, infact this data structure allows us to execute any type of computation effect/side effect via () => A function
      - type signature DOES describe the value it will produce. In this case it is an A
      - This does include side effect. Also, the construction of the program is SEPARATE from execution. Since in below this is a case class,
          where we create the instance of it. Unless we are calling unsafeRun this will not be executed. Hence separate! thereby this is an EFFECT!
   */

  // Inorder to make it a monad we implement map and flatMap
  private case class MyIO[A](unsafeRun: () => A):
    def map[B](f: A => B): MyIO[B] =
      MyIO(() => f(unsafeRun()))

    def flatMap[B](f: A => MyIO[B]): MyIO[B] =
      MyIO(() => f(unsafeRun()).unsafeRun())

  /*
    Exercise: create an IO which
    1. Measure the current time of the system
    2. Measure the duration of the computation
      - HINT - use exercise 1
             -use map and flatMap
    3. Read something from the console and return that line as an effect.
    4. Combine that reads from the console and something to the console
      println("Whats your name")
      read
      the print the message
   */

  // 1
  private val currentTime: MyIO[Long] = MyIO[Long](() => System.currentTimeMillis())

  // 2
  private def measure[A](computation: MyIO[A]): MyIO[(Long, A)] =
    for
      startTime <- currentTime
      result    <- computation
      endTime   <- currentTime
    yield (endTime - startTime, result)

  println(measure(MyIO { () =>
    Thread.sleep(1000); 42
  }).unsafeRun())

  // 3

  private val readLn: MyIO[String] = MyIO(() => scala.io.StdIn.readLine())

  // 4

  private val printIO:               MyIO[Unit] = readLn.map(println)
  private def printLn(line: String): MyIO[Unit] = MyIO(() => println(line))

  // printIO.unsafeRun()

  private val readerWriter: MyIO[Unit] =
    for
      _    <- printLn(s"Enter Name")
      line <- readLn
      _    <- printLn(s"Hello $line")
    yield ()

  readerWriter.unsafeRun()
