/*
rule = Zio2Upgrade
 */
package fix

import zio.{IO, RIO, Task, UIO, URIO, ZIO}

object ApplyReplacements {
  ZIO.attempt("blah")
  UIO.succeed("blah")
  URIO.succeed("blah")
  IO.attempt("blah")
  Task.attempt("blah")
  RIO.succeed("blah")
}
