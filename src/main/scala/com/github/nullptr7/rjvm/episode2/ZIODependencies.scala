package com.github.nullptr7
package rjvm
package episode2

import zio.*
import java.util.concurrent.TimeUnit
import java.io.IOException

object ZIODependencies /* extends ZIOAppDefault */:

  // app to subscribe users to newsletter
  case class User(name: String, email: String)

  class UserSubscription(emailService: EmailService, userDatabase: UserDatabase):
    def subscribeUser(user: User): Task[Unit] =
      for
        _ <- emailService.email(user)
        _ <- userDatabase.insert(user)
      yield ()

  object UserSubscription:
    def create(emailService: EmailService, userDatabase: UserDatabase): UserSubscription =
      new UserSubscription(emailService, userDatabase)

    val live: ZLayer[EmailService & UserDatabase, Nothing, UserSubscription] = ZLayer.fromFunction(create)

  class EmailService:
    def email(user: User): Task[Unit] =
      ZIO.succeed(println(s"You've just been subscribed, Welcome $user"))

  object EmailService:
    def create(): EmailService = new EmailService()

    val live: ZLayer[Any, Nothing, EmailService] = ZLayer.succeed(create())

  class UserDatabase(connectionPool: ConnectionPool):
    def insert(user: User): Task[Unit] =
      for
        conn <- connectionPool.get
        _    <- conn.runQuery(s"INSERT INTO SUBSCRIBERS(user, email) VALUES (${user.name}, ${user.email})")
      yield ()

  object UserDatabase:
    def create(connectionPool: ConnectionPool): UserDatabase =
      new UserDatabase(connectionPool)

    val live: ZLayer[ConnectionPool, Nothing, UserDatabase] = ZLayer.fromFunction(create)

  class ConnectionPool(nConnections: Int):
    def get: Task[Connection] =
      ZIO.succeed(println("Acquired Connection...")) *> ZIO.succeed(Connection())

  object ConnectionPool:
    def create(nConnections: Int): ConnectionPool =
      new ConnectionPool(nConnections)

    def live(nConnections: Int): ZLayer[Any, Nothing, ConnectionPool] = ZLayer.succeed(create(nConnections))

  class Connection():
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

  val program_v2: ZIO[UserSubscription, Throwable, Unit] =
    for
      _ <- subscribe_v2(User("Foo", "foo@boo.com"))
      _ <- subscribe_v2(User("Too", "too@boo.com"))
    yield ()

  /** ZLayers
    */

  private val connectionPoolLayer: ZLayer[Any, Nothing, ConnectionPool] =
    ZLayer.succeed(ConnectionPool.create(10))

  /*
    A ZLayer that requires a dependency (higher layer) can be built with ZLayer.fromFunction
    this API automatically(via macro) fetches the function arguments and place them into the ZLayer's
    dependency/environment type argument
   */
  private val databaseLayerUsingScala2:     ZLayer[ConnectionPool, Nothing, UserDatabase]                  = ZLayer.fromFunction(UserDatabase.create _) // if using scala2
  private val databaseLayerUsingScala3:     ZLayer[ConnectionPool, Nothing, UserDatabase]                  = ZLayer.fromFunction(UserDatabase.create)   // if using scala3
  private val emailServiceLayer:            ZLayer[Any, Nothing, EmailService]                             = ZLayer.succeed(EmailService.create())
  private val userSubscriptionServiceLayer: ZLayer[UserDatabase & EmailService, Nothing, UserSubscription] = ZLayer.fromFunction(UserSubscription.create)

  // The above ZLayers are dependent on one another in some way..
  // This allows us to compose the layers to form one big layer
  // By composing layers
  // Vertical Composition
  private val databaseLayerFull: ZLayer[Any, Nothing, UserDatabase] = connectionPoolLayer >>> databaseLayerUsingScala2

  // Horizontal Composition - combines the dependencies of both layers and values of both layers
  // In horizontal composition, the error channel is combined with the lowest common ancestor of both
  private val subscriptionRequirementsLayer: ZLayer[Any, Nothing, UserDatabase & EmailService] = databaseLayerFull ++ emailServiceLayer

  // We can also mix and match all the above layers
  private val userSubscriptionLayer: ZLayer[Any, Nothing, UserSubscription] =
    subscriptionRequirementsLayer >>> userSubscriptionServiceLayer

  private val runConfiguration = program_v2.provideLayer(userSubscriptionLayer)

  /*
    Best Practices: Write "factory" methods exposing layers in the companion objects of the services

    Create layers in the companion of services that you want to expose
   */
  private val databaseLayerFull_v2:                 ZLayer[Any, Nothing, UserDatabase]                             = ConnectionPool.live(10) >>> UserDatabase.live
  private val userSubscriptionRequirementsLayer_v2: ZLayer[Any, Nothing, UserDatabase & EmailService]              = databaseLayerFull_v2 ++ EmailService.live
  private val userSubscriptionServiceLayer_v2:      ZLayer[EmailService & UserDatabase, Nothing, UserSubscription] = UserSubscription.live
  private val userSubscriptionLayer_v2:             ZLayer[Any, Nothing, UserSubscription]                         = userSubscriptionRequirementsLayer_v2 >>> userSubscriptionServiceLayer_v2
  private val runConfiguration_v2:                  ZIO[Any, Throwable, Unit]                                      = program_v2.provideLayer(userSubscriptionLayer_v2)

  /*
    Some more important features of ZLayer are
   */
  // 1. Passthrough, in this case we pass the environment(input) of the ZLayer to the value channel
  //  Below one has both ConnectionPool and UserDatabase in the value channel
  private val dbWithPoolLayer: ZLayer[ConnectionPool, Nothing, ConnectionPool & UserDatabase] = UserDatabase.live.passthrough

  // 2. Service, in this case, we take a dependency and expose it as a value to futher layers
  private val dbService: ZLayer[UserDatabase, Nothing, UserDatabase] = ZLayer.service[UserDatabase]

  // 3. Launch, in this case, we create a service that never finishes
  // The output will be nothing since this ZIO effect will never be completed. This is usually used during some infinite process like server or game loop etc...
  private val infiniteService: ZIO[EmailService & UserDatabase, Nothing, Nothing] = UserSubscription.live.launch

  // 4. Memoize, this feature is active by default in ZIO, which guarantees that once the ZLayer is instantiated, the same ZLayer will be reused.
  // Unless we are explicitly calling .fresh api.
  private val userDatabaseFresh: ZLayer[ConnectionPool, Nothing, UserDatabase] = UserDatabase.live.fresh

  /*
    Already provided services
      Clock, Random, System, Clock
      This comes by default in ZIOAppDefault
   */
  private val currentTime:     UIO[Long]                             = Clock.currentTime(TimeUnit.SECONDS)
  private val randomValue:     UIO[Int]                              = Random.nextInt
  private val sysVariable:     IO[SecurityException, Option[String]] = System.env("JAVA_HOME")
  private val printLineEffect: IO[IOException, Unit]                 = Console.printLine("This is ZIO")

  /* override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = */
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
  program_v2.provideLayer(
    ZLayer.succeed(
      UserSubscription.create(
        EmailService.create(),
        UserDatabase.create(
          ConnectionPool.create(10)
        ),
      )
    )
  )
