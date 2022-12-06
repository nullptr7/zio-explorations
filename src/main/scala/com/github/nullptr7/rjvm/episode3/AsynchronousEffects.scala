package com.github.nullptr7
package rjvm
package episode3

import zio.*
import utils.*
import java.util.concurrent.Executors

object AsynchronousEffects extends ZIOAppDefault:

  // Callback based APIs in our JavaWorld
  private object LoginService:
    case class AuthError(message: String)
    case class UserProfile(email: String, name: String)

    // Lets say this service has its own thread pool
    val executor = Executors.newFixedThreadPool(8)

    // lets say we have a "database"
    val passwordDb = Map(
      "in@out.com" -> "ABC@123"
    )

    // lets also say we have a user profile
    val profileData = Map(
      "in@out.com" -> "Foo"
    )

    /** This is some kind of a traditional application written without effect tracking. However, the 'onSuccess' & 'onFailure' have callBacks
      * And this is asynchronous
      */
    def traditionalLogin(email: String, password: String)(onSuccess: UserProfile => Unit, onFailure: AuthError => Unit): Unit =
      executor.execute { () =>
        println(s"[${Thread.currentThread().getName}] Attempting to login for $email")
        passwordDb.get(email) match
          case Some(`password`) => onSuccess(UserProfile(email, profileData(email)))
          // case Some(p) if p == password => ???  --> This is same as above
          case Some(_)          => onFailure(AuthError("Incorrect password entered! Please try again."))
          case None             => onFailure(AuthError(s"User $email does not exist, Please signup."))
      }

    // There can be a scenario, where the above code is legacy and it is difficult to change and write fully effectful code. So,
    // Anotherway is to lift using ZIO with the same 'callbacks'

    def loginAsZIO(id: String, password: String): IO[LoginService.AuthError, LoginService.UserProfile] =
      ZIO.async[Any, LoginService.AuthError, LoginService.UserProfile] { cb => // this callback object is created by ZIO
        LoginService.traditionalLogin(
          id,
          password,
        )(
          profile => cb(ZIO.succeed(profile)), // Notify the ZIO fiber to complete the ZIO with a success
          error   => cb(ZIO.fail(error)),      // Notify the ZIO fiber to complete the ZIO with a failure
        )
      }

    val loginProgram =
      for
        email       <- Console.readLine("Enter email: ")
        pass        <- Console.readLine("Enter password: ")
        userProfile <- loginAsZIO(email, pass).debugThread
        _           <- Console.printLine(s"Welcome to the site, ${userProfile.name}")
      yield ()

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = LoginService.loginProgram
