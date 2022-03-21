package fix

import zio.{IO, RIO, Task, UIO, URIO, ZIO}

object ApplyReplacements {
  ZIO.attempt("blah")
  UIO.succeed("blah")
  URIO.succeed("blah")
  IO.attempt("blah")
  Task.attempt("blah")
  RIO.attempt("blah")

  val flatMap1 = ZIO.attempt(1).flatMap((x: Int) => ZIO.attempt(x + 1))

  val list = List(1, 2, 3)
  val res  = IO.foreachPar(list)(x => UIO.succeed(x.toString)).withParallelism(2)
}
