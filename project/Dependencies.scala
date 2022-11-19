import sbt._

object Dependencies {

  object ch {
    object `qos.logback` {
      val `logback-classic` =
        "ch.qos.logback" % "logback-classic" % "1.4.5"
    }
  }

  object com {
    object h2database {
      val h2 =
        "com.h2database" % "h2" % "2.1.214"
    }
  }

  object dev {
    object zio {
      val zio =
        "dev.zio" %% "zio" % "2.0.4"

      val `zio-test` =
        "dev.zio" %% "zio-test" % "2.0.4"

      val `zio-json` =
        "dev.zio" %% "zio-json" % "0.3.0"

      val `zio-config` =
        "dev.zio" %% "zio-config" % "3.0.2"

      val `zio-config-magnolia` =
        "dev.zio" %% "zio-config-magnolia" % "3.0.2"

      val `zio-config-typesafe` =
        "dev.zio" %% "zio-config-typesafe" % "3.0.2"

      val `zio-prelude` =
        "dev.zio" %% "zio-prelude" % "1.0.0-RC16"
    }
  }

  // avoid package issues with sbt.io
  object IO {
    object getquill {
      val `quill-zio` =
        "io.getquill" %% "quill-zio" % "4.6.0"

      val `quill-jdbc-zio` =
        "io.getquill" %% "quill-jdbc-zio" % "4.6.0"
    }

    object d11 {
      val zhttp =
        "io.d11" %% "zhttp" % "2.0.0-RC11"
    }
  }

  object org {
    object scalatest {
      val scalatest =
        "org.scalatest" %% "scalatest" % "3.2.14"
    }

    object slf4j {
      val `slf4j-api` =
        "org.slf4j" % "slf4j-api" % "2.0.4"
    }

    object scalatestplus {
      val `scalacheck-1-16` =
        "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0"
    }
  }
}
