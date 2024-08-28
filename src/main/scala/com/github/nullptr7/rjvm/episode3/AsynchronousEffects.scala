package com.github.nullptr7
package rjvm
package episode3

import zio.*
import utils.*
import java.util.concurrent.{ Executors, ExecutorService }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object AsynchronousEffects extends ZIOAppDefault:

  // Callback based APIs in our JavaWorld
  private object LoginService:
    private case class AuthError(message: String)
    private case class UserProfile(email: String, name: String)

    // Lets say this service has its own thread pool
    private val executor = Executors.newFixedThreadPool(8)

    // lets say we have a "database"
    private val passwordDb = Map(
      "in@out.com" -> "ABC@123"
    )

    // lets also say we have a user profile
    private val profileData = Map(
      "in@out.com" -> "Foo"
    )

    /** This is some kind of a traditional application written without effect tracking. However, the 'onSuccess' & 'onFailure' have callBacks
      * And this is asynchronous
      */
    private def traditionalLogin(email: String, password: String)(onSuccess: UserProfile => Unit, onFailure: AuthError => Unit): Unit =
      executor.execute { () =>
        println(s"[${Thread.currentThread().getName}] Attempting to login for $email")
        passwordDb.get(email) match
          case Some(`password`) => onSuccess(UserProfile(email, profileData(email)))
          // case Some(p) if p == password => ???  --> This is same as above
          case Some(_)          => onFailure(AuthError("Incorrect password entered! Please try again."))
          case None             => onFailure(AuthError(s"User $email does not exist, Please signup."))
      }

    // There can be a scenario, where the above code is legacy and it is difficult to change and write fully effectful code. So,
    // Another way is to lift using ZIO with the same 'callbacks'

    private def loginAsZIO(id: String, password: String): IO[LoginService.AuthError, LoginService.UserProfile] =
      ZIO.async[Any, LoginService.AuthError, LoginService.UserProfile] { cb => // this callback object is created by ZIO
        LoginService.traditionalLogin(
          id,
          password,
        )(
          profile => cb(ZIO.succeed(profile)), // Notify the ZIO fiber to complete the ZIO with a success
          error   => cb(ZIO.fail(error)),      // Notify the ZIO fiber to complete the ZIO with a failure
        )
      }

    val loginProgram: ZIO[Any, Object, Unit] =
      for
        email       <- Console.readLine("Enter email: ")
        pass        <- Console.readLine("Enter password: ")
        userProfile <- loginAsZIO(email, pass).debugThread
        _           <- Console.printLine(s"Welcome to the site, ${userProfile.name}")
      yield ()

  /** Exercises
    */
  // 1- Lift a computation running on some(external) thread to ZIO
  // hint: invoke the cb when the computation is complete
  // hint 2: don't wrap computation into a ZIO
  private def external2ZIO[A](computation: () => A)(executor: ExecutorService): Task[A] =
    ZIO.async[Any, Throwable, A] { cb =>
      executor.execute { () =>
        try
          val res = computation()
          cb(ZIO.succeed(res))
        catch case t: Throwable => cb(ZIO.fail(t))
      }
    }

  private val demoExternal2ZIO: ZIO[Any, Throwable, Unit] =
    val executor = Executors.newFixedThreadPool(4)
    val zio: Task[Int] = external2ZIO { () =>
      println(s"[${Thread.currentThread().getName}] computing the meaning of life on some thread")
      Thread.sleep(1000)
      42
    }(executor)
    zio.debugThread.unit

  // 2 - Lift a Future to a ZIO
  // hint: invoke cb on/when the future completes
  private def future2ZIO[A](future: => Future[A])(implicit ec: ExecutionContext): Task[A] =
    ZIO.async[Any, Throwable, A] { cb =>
      future.onComplete {
        case Failure(exception) => cb(ZIO.fail(exception))
        case Success(value)     => cb(ZIO.succeed(value))
      }
    }

  private lazy val demoFuture2ZIO: ZIO[Any, Throwable, Unit] =
    val executor = Executors.newFixedThreadPool(4)
    given ec: ExecutionContext = ExecutionContext.fromExecutorService(executor)
    val mol = future2ZIO(Future {
      println(s"[${Thread.currentThread().getName}] computing the meaning of life on some thread")
      Thread.sleep(1000)
      42
    })
    mol.debugThread.unit

  // 3 - Implement a never-ending ZIO
  // hint: ZIO.async fiber is semantically blocked until we call the callback
  private def neverEndingZIO[A]: UIO[A] =
    ZIO.async(_ => ())

  /**
   @note there is an API similar to above, which says ZIO.never, if we check that it calls
         <code>async(_ => ())</code>
  */

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = /*LoginService.loginProgram*/ demoExternal2ZIO *> demoFuture2ZIO
