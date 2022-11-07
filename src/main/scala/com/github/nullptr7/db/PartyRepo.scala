package com.github.nullptr7
package db

import models.*
import zio.*

trait PartyRepo:
  def insertParty(party: Party): ZIO[Any, Throwable, Party]

  def insertVote(vote: Vote): ZIO[Any, Nothing, Unit]

  def getVote(nationalIdNumber: Nin): ZIO[Any, Nothing, Option[String]]

  private[db] def deleteAllRows: ZIO[Any, Nothing, Unit]
