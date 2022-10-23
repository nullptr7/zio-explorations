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
    .aggregate(zioServer, zioConfig)

lazy val zioServer =
  project
    .settings(name := "zioServer")
    .settings(commonSettings)
    .settings(commonDependencies)
    .settings(serverDependencies)

lazy val zioConfig =
  project
    .settings(name := "zioConfig")
    .settings(commonSettings)
    .settings(commonDependencies)
    .settings(configDependencies)

/*
 *  Project Dependencies
 */
lazy val serverDependencies =
  libraryDependencies ++= Seq(
    dev.zio.`zio-json`,
    io.d11.zhttp,
  )

lazy val configDependencies =
  libraryDependencies ++= Seq(
    dev.zio.`zio-config`,
    dev.zio.`zio-config-magnolia`,
    dev.zio.`zio-config-typesafe`,
  )

lazy val commonDependencies = Seq(
  libraryDependencies ++= Seq(
    // main dependencies
    dev.zio.zio,
    // io.getquill.`quill-zio`,
    // io.getquill.`quill-jdbc-zio`,
    // com.h2database.h2,
    ch.`qos.logback`.`logback-classic`,
    org.slf4j.`slf4j-api`,
  ),
  libraryDependencies ++= Seq(
    org.scalatest.scalatest,
    org.scalatestplus.`scalacheck-1-16`,
  ).map(_ % Test),
)

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
