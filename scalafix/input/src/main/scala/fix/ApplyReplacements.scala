/*
rule = Zio2Upgrade
 */
package fix

import zio.{IO, RIO, Task, UIO, URIO, ZIO}

object ApplyReplacements {
  ZIO("blah")
  UIO("blah")
  URIO("blah")
  IO("blah")
  Task("blah")
  RIO("blah")

  val flatMap1 = ZIO(1).flatMap((x: Int) => ZIO(x + 1))

  val list = List(1, 2, 3)
  val res  = IO.foreachParN(2)(list)(x => UIO(x.toString))
}
