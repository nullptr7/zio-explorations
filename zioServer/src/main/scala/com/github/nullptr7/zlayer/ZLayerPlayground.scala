package com.github.nullptr7
package zlayer

import zio.*, zio.Console.*
import com.github.nullptr7.zlayer.ZLayerPlayground.UserEmailer.Service

object ZLayerPlayground extends zio.ZIOAppDefault:
  private val meaningOfLife: ZIO[Any, Nothing, Int] = ZIO.succeed(42)
  private val aFailure = ZIO.fail(new RuntimeException("Boom"))

  private val greeting =
    for
      _     <- aFailure
      _     <- printLine("Enter something")
      value <- readLine
      thunk <- printLine(s"You have entered ${value.toInt / 100}")
    yield thunk

  case class User(name: String, email: String)

  object UserEmailer:
    // service definition
    trait Service:
      def notify(user: User, message: String): Task[Unit]

    // service implementation
    val live: ZLayer[Any, Nothing, UserEmailer.Service] = ZLayer.succeed(
      new Service:
        override def notify(user: User, message: String): Task[Unit] = ZIO.from[Unit] {
          println(s"[User Emailer] Sending '$message' to ${user.email}")
        }

    )

    // front facing API
//    def notify(user: User, message: String): ZIO[UserEmailer.Service, Throwable, Unit] =
//      ZIO.environmentWithZIO[UserEmailer.Service](hasService =>
//        hasService.get.notify(user, message)
//      )

  object UserDb:
    trait Service:
      def insert(user: User): Task[Unit]

    val live: ZLayer[Any, Nothing, UserDb.Service] = ZLayer.succeed(
      new Service:
        override def insert(user: User): Task[Unit] = ZIO.from[Unit] {
          println(s"[User Db] Inserting user $user")
        }

    )

//    def insertUser(user: User): ZIO[UserDb.Service, Throwable, Unit] =
//      ZIO.environmentWithZIO[UserDb.Service](_.get.insert(user))

  // Horizontal Composition
  private val userBackendService: ZLayer[Any, Nothing, UserEmailer.Service & UserDb.Service] =
    UserEmailer.live ++ UserDb.live

  // Vertical Composition
  private object UserSubscription:
    class Service(notifier: UserEmailer.Service, userDb: UserDb.Service):
      def subscribe(user: User): Task[User] =
        for {
          _ <- userDb.insert(user)
          _ <- notifier.notify(user, s"Welcome ${user.name}")
        } yield user

    val live: ZLayer[UserEmailer.Service & UserDb.Service, Nothing, UserSubscription.Service] =
      ZLayer {
        for {
          userEmailer <- ZIO.service[UserEmailer.Service]
          userDb      <- ZIO.service[UserDb.Service]
        } yield UserSubscription.Service(userEmailer, userDb)
      }

    def subscribe(user: User): ZIO[UserSubscription.Service, Throwable, User] =
      ZIO.environmentWithZIO[UserSubscription.Service](_.get.subscribe(user))

  private val userSubscriptionLayer: ZLayer[Any, Nothing, UserSubscription.Service] =
    userBackendService >>> UserSubscription.live

  override def run: URIO[Any, ExitCode] =
    /*UserDb
      .insertUser(User("Foo", "foo@boo.com"))
      .provideLayer(userBackendService)
      .exitCode *>
      UserEmailer
        .notify(User("Foo", "Foo@Boo.com"), "Welcome to Scala 3 with ZIO 2.0")
        .provideLayer(userBackendService)
        .exitCode *>*/
      UserSubscription
        .subscribe(User("Foo", "Foo@Boo.com"))
        .provideLayer(userSubscriptionLayer)
        .exitCode
