import zio.test.*
import zio.*

case class Counter(value: Ref[Int]):
  def inc: UIO[Unit] = value.update(_ + 1)
  def get: UIO[Int]  = value.get

object Counter:
  val layer: ZLayer[Any, Nothing, Counter] =
    ZLayer.scoped(
      ZIO.acquireRelease(
        Ref.make(0).map(Counter(_)) <* ZIO.debug("Counter initialized!")
      )(c => c.get.debug("Number of tests executed"))
    )
  def inc:   ZIO[Counter, Nothing, Unit]   = ZIO.service[Counter].flatMap(_.inc)

object ZIOTestExample extends ZIOSpecDefault:

  def spec = {
    suite("Spec1")(
      test("test1") {
        assertTrue(true)
      } @@ TestAspect.after(Counter.inc),
      test("test2")(assertTrue(true)) @@ TestAspect.after(Counter.inc),
    ) +
      suite("Spec2") {
        test("test1") {
          assertTrue(true)
        } @@ TestAspect.after(Counter.inc)
      }
  }.provideShared(Counter.layer)
