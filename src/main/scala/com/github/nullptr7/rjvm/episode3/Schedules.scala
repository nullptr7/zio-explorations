package com.github.nullptr7
package rjvm
package episode3

import zio.*
import utils.*

object Schedules extends ZIOAppDefault:
  private val aZIO = Random.nextBoolean.flatMap { flag =>
    if flag then ZIO.succeed("fetched value...").debugThread
    else ZIO.succeed("failure...").debugThread *> ZIO.fail("error")
  }

  // schedules are data structures that describes HOW effects should be timed
  private val aRetriedZIO = aZIO.retry(Schedule.recurs(10)) // retries 10 times, return first success or last failure

  // other retry APIs
  private val oneTimeSchedule   = Schedule.once       // this is going to be tried just once.
  private val recurrentSchedule = Schedule.recurs(10) // this is going to be tried 10 more times, return first success or last failure

  // Note: In this case, it will be an infinite operation meaning, it will keep on retrying for every x.seconds until ZIO.success(...) is received.
  // However, this operation is non blocking, because every failure ZIO runtime deschedules the working fiber
  private val fixedIntervalSchedule = Schedule.spaced(1.seconds) // This is going to be tried every 1s until success is received.

  /* exponential backoff:
    This kind of API should be used when let's say we have a database that may go down for some reason and this database is connected by multiple effects(ZIO)
  Now, when the DB comes back up, there may be a possibility that all those effects that were dependent on this db will be trying to establish the connection.
  This may result in DB going down because of traffic. Hence we can try using exponential backoff.
   */
  // In below, this starts with 1.second and then multiplies with the factor like 2nd iteration will be 2 seconds, 3rd will be 4 seconds and so on...
  private val anExponentialBackoff = Schedule.exponential(1.second, 2.0)

  private val aFiboSchedule = Schedule.fibonacci(1.second) // 1s, 1s, 2s, 3s, 5s, 8s and so on...

  // combinators
  private val recurrentAndSpaced = Schedule.recurs(3) && Schedule.spaced(1.second) // every attempt is 1second apart and 3 attempts total.

  // sequencing scheduling
  private val recurrentThenSpaced = Schedule.recurs(3) ++ Schedule.spaced(1.second) // this will attempt 3 retries if those pass good, else it will retry every 1 second

  /*
    Schedules returns 4 time arguments [-R, -I, +O, Duration]
    R = environment, much similar to like ZIO's R
    I = input (errors in the case of .retry, so that we can inspect the failure and then we decide wether to retry or not.
        It has values in the case of .repeat so that we can inspect the value)
    O = output (values for the next schedule so that you can do something with them.
   */
  private val totalTimeElapsed = Schedule.spaced(1.second) >>> Schedule.elapsed.map(time => println(s"total time take $time"))

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = aZIO.retry(totalTimeElapsed)
