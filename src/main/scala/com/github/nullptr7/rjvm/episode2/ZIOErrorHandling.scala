package com.github.nullptr7
package rjvm
package episode2

import scala.util.{ Failure, Success, Try }
import zio.*
import java.io.IOException

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

  /*
    Errors: Failure present in ZIO type signature (like "checked exception")
    Defects: Failure are unrecoverable, unforseen and not present in ZIO type signature.
   */

  val divisionByZero: UIO[Int] = ZIO.succeed(1 / 0)

  /** ZIO[R, E, A] can finish with Exit[E, A]
    * Success[A] containing a value
    * Cause[E]
    *   - Fail[E] containing the error, which ZIO cleanly caught and failed
    *   - Die(t: Throwable) which was unforseen like divisionByZero
    */

  val aFailedInt:           IO[String, Int]        = ZIO.fail("I failed!")
  val failureExposed:       IO[Cause[String], Int] = aFailedInt.sandbox
  val failureExposedHidden: IO[String, Int]        = failureExposed.unsandbox

  // Similarly we too have foldCause and foldZIOCause

  val foldedWithCause = aFailedInt.foldCause(
    cause => s"Failed with cause ${cause.defects}",
    value => s"Passed with value $value",
  )

  val foldedWithCause_v2 = aFailedInt.foldCauseZIO(
    cause => ZIO.succeed(s"Failed with cause ${cause.defects}"),
    value => ZIO.succeed(s"Passed with value $value"),
  )

  /** Good Practice
    *  - at a lower level, your "errors" should be treated
    *  - at a higher level, you should "hide" errors and assume that they are unrecoverable
    *
    * for e.g. if any errors occurs in BusinessLogic, we should ideally handle that error effectively
    * and when send the info back to the API/user/UI it should be not recoverable, meaning the error is a genuine error
    * and should be handle by correct request/input etc.
    *
    * Like SQL exception, it is a runtime exception but this should effectively handle by our application and the response
    * should be sent back to the user as unrecoverable and with meaningful business error message
    */

  def callHttpEndpoint(url: String): IO[IOException, String] =
    ZIO.fail(new IOException("No internet available"))

  val endpointCallWithDefects: UIO[String] =
    callHttpEndpoint("abc.com").orDie

  def callHttpEndpointWideError(url: String): IO[Exception, String] =
    ZIO.fail(new IOException("Wide IP Error"))

  def callHttpEndpoint_v2(url: String): IO[IOException, String] =
    callHttpEndpointWideError(url).refineOrDie {
      case ioe: IOException => ioe
      case _:   Exception   => new IOException("Generic Exception")
    }

  // Turn defects into the error channel
  val endpointCallWithError: IO[String, String] = endpointCallWithDefects.unrefine {
    case e: Throwable => e.getMessage()
  }

  val endpointCallWithError_v2 = callHttpEndpoint("dbcas.com").unrefineWith {
    case e: Throwable => ZIO.fail(e.getMessage())
  }(_ => ZIO.fail("Boom Shaka"))

  /*
    Combine effects with different errors
   */

  private case class IndexError(message: String)
  private case class DbError(message: String)

  private val callApi:    IO[IndexError, String] = ZIO.succeed("fetching from page")
  private val callFromDb: IO[DbError, Int]       = ZIO.succeed(1)

  // This feature is only available in scala 3 where we can use union types to find the appropriate error
  private val combined_mode1: IO[IndexError | DbError, (String, Int)] =
    for
      page <- callApi
      db   <- callFromDb
    yield (page, db)

  // In scala 2.x this is usless as we lose type safety as object does not mean anything
  private val combined_mode2: IO[Object, (String, Int)] =
    for
      page <- callApi
      db   <- callFromDb
    yield (page, db)

  /*
    Solutions:
      - design an error model create error ADTs sealed trait and so on...
      - use scala 3 union types
      -  use .mapError to some common error type
   */

  /** Exercise:
    */

  // 1 - make this effect a TYPED failure
  val aBadFailure: ZIO[Any, Nothing, Int] = ZIO.succeed[Int](throw new RuntimeException("This is bad"))

  val aTypedFailure: ZIO[Any, Throwable, Int] = aBadFailure.catchAllDefect(ZIO.fail(_))

  val aTypedFailure_v2: ZIO[Any, Cause[Nothing], Int] = aBadFailure.sandbox // Exposes the defect in the cause

  val aTypedFailure_v3: ZIO[Any, Throwable, Int] = aBadFailure.unrefine { // Surfaces out the exception to the error channel
    case e => e
  }

  // 2 - Take some ZIOs that can fail with throwable and just surface out a bunch of exceptions
  // transform a zio to another zio with narrower exception type
  def ioException[R, A](zio: ZIO[R, Throwable, A]): ZIO[R, IOException, A] =
    zio.refineOrDie {
      case io: IOException => new IOException(io.getMessage()) // This means that we are only taking IO rest will be considered as defect
    }

  // 3 - Work with Either and expose the undesired value in the value side or combnie either in error channel
  def left[R, E, A, B](zio: ZIO[R, E, Either[A, B]]): ZIO[R, Either[E, A], B] =
    zio.foldZIO(
      e => ZIO.fail(Left(e)),
      e =>
        e match {
          case Left(a)  => ZIO.fail(Right(a))
          case Right(b) => ZIO.succeed(b)
        },
    )

  // 4 - deal with errors both on error channel and value channel
  val database = Map("Daniel" -> 123, "alice" -> 789)

  case class QueryError(reason: String)
  case class UserProfile(name: String, phone: Int)

  def lookupProfile(userId: String): ZIO[Any, QueryError, Option[UserProfile]] =
    if userId != userId.toLowerCase() then ZIO.fail(QueryError("UserId is not valid"))
    else ZIO.succeed(database.get(userId).map(phone => UserProfile(userId, phone)))

  // surface out all the failed cases of this API
  // ZIO[Any, <all_possible_errors>, UserProfile]
  def betterLookupProfile(userId: String): ZIO[Any, Option[QueryError], UserProfile] =
    if userId != userId.toLowerCase() then ZIO.fail(Some(QueryError("UserId is not valid")))
    else
      database.get(userId) match
        case None        => ZIO.fail(Option.empty[QueryError])
        case Some(phone) => ZIO.succeed(UserProfile(userId, phone))

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = aTypedFailure_v3
