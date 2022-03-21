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
}
