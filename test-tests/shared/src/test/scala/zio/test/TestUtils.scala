package zio.test

import zio.{Console, ExecutionStrategy, UIO, Scope, ZLayer}

object TestUtils {

  def execute[E](spec: ZSpec[TestEnvironment with Scope, E]): UIO[Summary] =
    TestExecutor
      .default(
        testEnvironment ++ ZLayer.environment[Scope],
        (Console.live >>> TestLogger.fromConsole >>> ExecutionEventPrinter.live >>> TestOutput.live >>> ExecutionEventSink.live)
      )
//      .default(testEnvironment ++ ZLayer.environment[Scope])
      .run(spec, ExecutionStrategy.Sequential)

  def isIgnored[E](spec: ZSpec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec)
      .map(_.ignore > 0)

  def succeeded[E](spec: ZSpec[TestEnvironment, E]): UIO[Boolean] =
    execute(spec).map { summary =>
      summary.fail == 0
    }
}
