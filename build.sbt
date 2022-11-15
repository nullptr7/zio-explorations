import Dependencies._
import Dependencies.{ IO => io }

ThisBuild / organization := "com.github"
ThisBuild / scalaVersion := "3.2.0"

ThisBuild / scalacOptions ++=
  Seq(
    "-deprecation",
    "-rewrite",
    "-indent",
    "-explain",
    "-feature",
    "-language:implicitConversions",
    "-unchecked",
    "-Xfatal-warnings",
    "-Yexplicit-nulls", // experimental (I've seen it cause issues with circe)
    "-Ykind-projector",
    "-Ysafe-init",      // experimental (I've seen it cause issues with circe)
  ) /* ++ Seq("-rewrite", "-indent") ++ Seq("-source", "future-migration") */

lazy val app =
  project
    .in(file("."))
    .settings(name := "app")
    .settings(publish := {}, publish / skip := true)
    .settings(commonSettings)
    .settings(libraryDependencies ++= testDep ++ zioDep ++ loggingDep)

lazy val zioDep =
  Seq(
    dev.zio.zio,
    dev.zio.`zio-json`,
    dev.zio.`zio-config`,
    dev.zio.`zio-config-magnolia`,
    dev.zio.`zio-config-typesafe`,
    dev.zio.`zio-prelude`,
    dev.zio.`zio-test`,
    io.d11.zhttp,
  )

lazy val loggingDep =
  Seq(
    ch.`qos.logback`.`logback-classic`,
    org.slf4j.`slf4j-api`,
  )

lazy val testDep =
  Seq(
    org.scalatest.scalatest,
    org.scalatestplus.`scalacheck-1-16`,
  ).map(_ % Test)

/*
 * Project Settings
 */
lazy val commonSettings = {
  lazy val commonScalacOptions = Seq(
    Compile / console / scalacOptions --= Seq(
      "-Wunused:_",
      "-Xfatal-warnings",
    ),
    Test / console / scalacOptions :=
      (Compile / console / scalacOptions).value,
  )

  lazy val otherCommonSettings = Seq(
    update / evictionWarningOptions := EvictionWarningOptions.empty
  )

  Seq(
    commonScalacOptions,
    otherCommonSettings,
  ).reduceLeft(_ ++ _)
}
