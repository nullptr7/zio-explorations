import sbt._

object Dependencies {

  object ch {
    object `qos.logback` {
      val `logback-classic` =
        "ch.qos.logback" % "logback-classic" % "1.4.7"
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
        "dev.zio" %% "zio" % "2.1.6"

      val `zio-test` =
        "dev.zio" %% "zio-test" % "2.1.4" % Test

      val `zio-test-sbt` =
        "dev.zio" %% "zio-test-sbt" % "2.1.4" % Test

      val `zio-test-magnolia` =
        "dev.zio" %% "zio-test-magnolia" % "2.1.4" % Test

      val `zio-json` =
        "dev.zio" %% "zio-json" % "0.6.2"

      val `zio-config` =
        "dev.zio" %% "zio-config" % "4.0.2"

      val `zio-config-magnolia` =
        "dev.zio" %% "zio-config-magnolia" % "4.0.1"

      val `zio-config-typesafe` =
        "dev.zio" %% "zio-config-typesafe" % "4.0.2"

      val `zio-http` =
        "dev.zio" %% "zio-http" % "3.0.1+97-29d12531-SNAPSHOT"

      val `zio-http-test` =
        "dev.zio" %% "zio-http-testkit" % "3.0.1" % Test

      val `zio-prelude` =
        "dev.zio" %% "zio-prelude" % "1.0.0-RC16"
    }
  }

  // avoid package issues with sbt.io
  object IO {
    object getquill {
      val `quill-zio` =
        "io.getquill" %% "quill-zio" % "4.6.0.1"

      val `quill-jdbc-zio` =
        "io.getquill" %% "quill-jdbc-zio" % "4.6.0.1"
    }

    object d11 {
      val zhttp =
        "io.d11" %% "zhttp" % "2.0.0-RC11"
    }
  }

  object org {

    object apache {

      val `httpcomponents-client5` =
        "org.apache.httpcomponents.client5" % "httpclient5" % "5.3"
    }

    object scalatest {
      val scalatest =
        "org.scalatest" %% "scalatest" % "3.2.15"
    }

    object slf4j {
      val `slf4j-api` =
        "org.slf4j" % "slf4j-api" % "2.0.5"
    }

    object scalatestplus {
      val `scalacheck-1-16` =
        "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0"
    }
  }
}
