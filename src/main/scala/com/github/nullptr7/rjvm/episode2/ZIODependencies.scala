package com.github.nullptr7
package rjvm
package episode2

import zio.*

object ZIODependencies extends ZIOAppDefault:

  // app to subscribe users to newsletter
  private case class User(name: String, email: String)

  private class UserSubscription(emailService: EmailService, userDatabase: UserDatabase):
    def subscribeUser(user: User): Task[Unit] =
      for
        _ <- emailService.email(user)
        _ <- userDatabase.insert(user)
      yield ()

  private object UserSubscription:
    def create(emailService: EmailService, userDatabase: UserDatabase): UserSubscription =
      new UserSubscription(emailService, userDatabase)

  private class EmailService:
    def email(user: User): Task[Unit] =
      ZIO.succeed(println(s"You've just been subscribed, Welcome $user"))

  private object EmailService:
    def create(): EmailService = new EmailService()

  private class UserDatabase(connectionPool: ConnectionPool):
    def insert(user: User): Task[Unit] =
      for
        conn <- connectionPool.get
        _    <- conn.runQuery(s"INSERT INTO SUBSCRIBERS(user, email) VALUES (${user.name}, ${user.email})")
      yield ()

  private object UserDatabase:
    def create(connectionPool: ConnectionPool): UserDatabase =
      new UserDatabase(connectionPool)

  private class ConnectionPool(nConnections: Int):
    def get: Task[Connection] =
      ZIO.succeed(println("Acquired Connection...")) *> ZIO.succeed(Connection())

  private object ConnectionPool:
    def create(nConnections: Int): ConnectionPool =
      new ConnectionPool(nConnections)

  private class Connection():
    def runQuery(query: String): Task[Unit] =
      ZIO.succeed(println(s"Executing query $query"))

  // There are several way to DI, the most simplest way is to create instances with new operator like below, but this is not recommend by ZIO as this is not initialized correctly
  // This still works, just that it is not recommended
  private val userSubscriptionService =
    ZIO.succeed(
      new UserSubscription(
        new EmailService(),
        new UserDatabase(
          new ConnectionPool(10)
        ),
      )
    )

  // Another classic way to create a factory method i.e. apply or create or make methods in companion objects
  private val userSubscriptionService_v2 =
    ZIO.succeed(
      UserSubscription.create(
        EmailService.create(),
        UserDatabase.create(
          ConnectionPool.create(10)
        ),
      )
    )

  /*
    Although a clean way but there are few drawbacks
    - does not scale up and it is difficult to maintain in realworld if we have deep nesting of the dependencies
    - DI in this case can be 100x worse
        - pass dependencies partially will be difficult and difficult to debug
        - not having all the depdencies in the same place
        - passing dependencies multiple times can be an issue, consider if the class/service itself does not have any dependency
            but the argument/API requires it

        for e.g. below 'anotherService' is not passed from the class but to the API 'subscribeUser', this can lead to
            defining dependency multiple times leading to resource leaks

        private class UserSubscription(emailService: EmailService, userDatabase: UserDatabase):
            def subscribeUser(user: User, anotherService: AnotherService): Task[Unit] =
            for
                _ <- emailService.email(user, anotherService)
                _ <- userDatabase.insert(user)
            yield ()

    Another issue will be for example below
    private def subscribe(user: User) =
        for {
        sub <- userSubscriptionService_v2 // service in instantiated at the point of call
        _   <- sub.subscribeUser(user)
        } yield ()

    userSubscriptionService_v2 is initialized multiple times which can lead to resource leaking
    i.e. when we subscribe multiple users with same effect program

    val program =
        for
            _ <- subscribe(User("foo", "foo@boo.com")) //instantiates here
            _ <- subscribe(User("too", "too@boo.com")) //instantiates here too
        yield ()

    Another drawback is, we can always lose track of the userSubscriptionService if the subscribe(..)
    is being used by many places of the code that lie in different file. Due to which, this will spin up userSubscription
    everytime
   */

  private def subscribe(user: User): Task[Unit] =
    for {
      sub <- userSubscriptionService_v2 // service in instantiated at the point of call
      _   <- sub.subscribeUser(user)
    } yield ()

  // alternative is we enclose the userSubscription in the effect itself and will be provided at the end of the world
  private def subscribe_v2(user: User): ZIO[UserSubscription, Throwable, Unit] =
    for
      sub <- ZIO.service[UserSubscription]
      _   <- sub.subscribeUser(user)
    yield ()

  // The only difference from the previous method is the subscribe_v2 method takes the environment R from ZIO's R

  private val progam_v2 =
    for
      _ <- subscribe_v2(User("Foo", "foo@boo.com"))
      _ <- subscribe_v2(User("Too", "too@boo.com"))
    yield ()

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    /*
      Advantages:
      - we don't need to care about the dependencies until the end of the world
      - all ZIOs requiring the dependency will use the SAME instances, thereby removing resource leaks
      - can use different instances of the same type for different needs (e.g. testing), this we could not do effectively 
          in our previous case because in below userSubscriptionService_v2 will be the same hence difficult to test and
          provide mock userSubscriptionService
          private def subscribe(user: User): Task[Unit] =
            for {
              sub <- userSubscriptionService_v2 // service in instantiated at the point of call
              _   <- sub.subscribeUser(user)
            } yield ()

      - ZLayers can be created and composed much like the regular ZIOs + rich APIs
    */
    progam_v2.provideLayer(
      ZLayer.succeed(
        UserSubscription.create(
          EmailService.create(),
          UserDatabase.create(
            ConnectionPool.create(10)
          ),
        )
      )
    )
