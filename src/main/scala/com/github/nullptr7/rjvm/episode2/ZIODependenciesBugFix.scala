package com.github.nullptr7
package rjvm
package episode2

import zio.*

import ZIODependencies.*

/** Scala3 with ZIO bug throws compilation issue if we do below way of providing all the layers and ZIO with help of macros
  * finds and autowires based on the dependency graph and injects all and creates one big layer.
  */
object ZIODependenciesBugFix extends ZIOAppDefault:

  // Another way is below
  private val userSubscriptionLayer_v3: ZLayer[Any, Nothing, UserSubscription] = ZLayer.make[UserSubscription](
    UserSubscription.live,
    ConnectionPool.live(10),
    EmailService.live,
    UserDatabase.live,
  )

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = program_v2.provide(
    UserSubscription.live,
    ConnectionPool.live(10),
    EmailService.live,
    UserDatabase.live,
    // ZLayer.Debug.tree // This is only for debug purposes
    // ZLayer.Debug.mermaid // For Debugging
  )
