package com.github.nullptr7
package rjvm
package episode2

import scala.util.{Failure, Success, Try}
import zio.*

object ZIOErrorHandling extends ZIOAppDefault:

  // ZIOs can fail
  val aFailedZIO          = ZIO.fail("Something went wrong")
  val failedWithThrowable = ZIO.fail(new RuntimeException("Boom"))

  // We can also change the failed to some other failure scenario
  val failedWithDescription: ZIO[Any, String | Null, Nothing] = failedWithThrowable.mapError(_.getMessage)

  // attempt: run an effect that can throw an exception

  val badZIO = ZIO.succeed {
    println("Trying something")
    val string: String | Null = null
    string.length()
  } // This is bad

  // Use attempt if you are not sure what you code might throw
  val aBetterZIO: Task[Int] = ZIO.attempt {
    println("Trying something")
    val string: String | Null = null
    string.nn.length()
  }

  // effectfully catch errors
  val catchError:  ZIO[Any, Throwable, Matchable] = aBetterZIO.catchAll(e => ZIO.attempt(s"Returning a different value because $e"))
  val catchError1: ZIO[Any, Nothing, Matchable]   = aBetterZIO.catchAll(e => ZIO.succeed(s"Returning a different value because $e"))

  val catchSelectiveErrors: ZIO[Any, Throwable, Matchable] = aBetterZIO.catchSome {
    case e: RuntimeException => ZIO.succeed(s"Ignoring runtime exception $e")
    case _ => ZIO.succeed("Ignoring everything else")
  }

  // This takes lowest common ancestor i.e. common between String and Throwable and the common is Object or Serializable
  val catchSelectiveErrorsV2: ZIO[Any, Object, Matchable] = aBetterZIO.catchSome {
    case e: RuntimeException => ZIO.succeed(s"Ignoring runtime exception $e")
    case _ => ZIO.fail("Ignoring everything else")
  }

  // chain effects
  val aBetterAttempt: ZIO[Any, Nothing, Int] = aBetterZIO.orElse(ZIO.succeed(56)) // This ZIO effect will never fail hence Nothing

  // fold: which would handle both success and failure
  val handleBoth: URIO[Any, String] = aBetterZIO.fold(ex => s"Something bad happened $ex", value => s"Length of the string was $value")

  // effectful fold: foldZIO
  val handleBoth_v2 = aBetterZIO.foldZIO(
    ex    => ZIO.succeed(s"Something bad happened $ex"),
    value => ZIO.succeed(s"Length of the string was $value"),
  )

  /*
    Conversion between Option/Try/Either to ZIO
   */

  val aTryToZIO: Task[Int] = ZIO.fromTry(Try(42 / 0)) // can fail with Throwable

  // either to ZIO
  val anEither:      Either[Int, String] = Right("Success")
  val anEitherToZIO: IO[Int, String]     = ZIO.fromEither(anEither)

  // ZIO to ZIO with Either in the value channel
  val eitherZIO: URIO[Any, Either[Throwable, Int]] = aBetterZIO.either // This ZIO will never fail as we are handling error in either only in the value channel

  // reverse of above -> this basically splits the either where left will be error channel in ZIO and success will be in the right channel
  val reverseEither = eitherZIO.absolve

  // option to ZIO
  /** The reason we have option[Nothing] is, we do not know if the fromOption failed or passed.
    * We just know that the value is absent, hence the None type. So we cannot put throwable but we can obviously put Option[Nothing]
    */
  val anOption: ZIO[Any, Option[Nothing], Int] = ZIO.fromOption(Some(42))

  /** Exercise: implement a version of fromTry, fromOption, fromEither, either, absolve
    * using fold and foldZIO
    */

  def customFromTry[A](tried: Try[A]): Task[A] =
    tried match
      case Failure(exception) => ZIO.fail(exception)
      case Success(value)     => ZIO.succeed(value)

  def customFromEither[E, A](either: Either[E, A]): IO[E, A] =
    either match
      case Left(error)  => ZIO.fail(error)
      case Right(value) => ZIO.succeed(value)

  def customFromOption[A](option: Option[A]): IO[Option[Nothing], A] =
    option match
      case None        => ZIO.fail(Option.empty[Nothing])
      case Some(value) => ZIO.succeed(value)

  def customMoveToValueChannelEither[E, A](either: Either[E, A]): UIO[Either[E, A]] = ZIO.succeed(either)

  def zioToZIOEither[R, E, A](zio: ZIO[R, E, A]): URIO[R, Either[E, A]] =
    zio.foldZIO(
      error => ZIO.succeed(Left(error)),
      value => ZIO.succeed(Right(value)),
    )

  def customAbsolve[E, A](uio: UIO[Either[E, A]]): IO[E, A] =
    uio.flatMap {
      _ match
        case Left(value)  => ZIO.fail(value)
        case Right(value) => ZIO.succeed(value)
    }

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = ???
