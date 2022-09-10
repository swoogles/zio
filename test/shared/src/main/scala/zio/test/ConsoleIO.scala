package zio.test

import zio.test.ZTestLogger.LogEntry

private[test] sealed trait ConsoleIO
private[test] object ConsoleIO {
  case class Input(line: String)  extends ConsoleIO
  case class Output(line: String) extends ConsoleIO

  case class LogLine(logLevel: LogEntry) extends ConsoleIO
}
