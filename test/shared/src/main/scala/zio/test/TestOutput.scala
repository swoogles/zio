package zio.test

import zio.{Chunk, Ref, ZIO, ZLayer}

trait TestOutput {

  /**
   * Does not necessarily print immediately. Might queue for later, sensible
   * output.
   */
  def print(
    executionEvent: ExecutionEvent
  ): ZIO[Any, Nothing, Unit]
}

object TestOutput {
  val live: ZLayer[ExecutionEventPrinter, Nothing, TestOutput] =
    ZLayer.fromZIO(
      for {
        executionEventPrinter <- ZIO.service[ExecutionEventPrinter]
        outputLive            <- TestOutputLive.make(executionEventPrinter)
      } yield outputLive
    )

  /**
   * Guarantees:
   *   - Everything at or below a specific suite level will be printed
   *     contiguously
   *   - Everything will be printed, as long as required SectionEnd events have
   *     been passed in
   *
   * Not guaranteed:
   *   - Ordering within a suite
   */
  def print(
    executionEvent: ExecutionEvent
  ): ZIO[TestOutput, Nothing, Unit] =
    ZIO.serviceWithZIO[TestOutput](_.print(executionEvent))

  case class TestOutputLive(
    output: Ref.Synchronized[Map[SuiteId, Chunk[ExecutionEvent]]],
    reporters: TestReporters,
    executionEventPrinter: ExecutionEventPrinter
  ) extends TestOutput {

    private def getAndRemoveSectionOutput(id: SuiteId, action: Chunk[ExecutionEvent] => ZIO[Any, Nothing, Chunk[ExecutionEvent]], parentAction: Chunk[ExecutionEvent] => Option[(SuiteId, Chunk[ExecutionEvent])] = _ => None) =
      output
        .getAndUpdateZIO { initial =>
          val contents = initial.getOrElse(id, Chunk.empty)
          action(contents).map { newContents =>
            val newRes = updatedWith(initial, id)(_ => Some(newContents))
            parentAction(contents) match {
              case Some(value) => updatedWith(newRes, value._1)(_.map(_ ++ value._2))
              case None => newRes
            }
          }
        }
        .map(_.getOrElse(id, Chunk.empty)) // TODO remove when done

    def print(
      executionEvent: ExecutionEvent
    ): ZIO[Any, Nothing, Unit] = {
      executionEvent match {
        case end: ExecutionEvent.SectionEnd =>
          printOrFlush(end)

        case flush: ExecutionEvent.TopLevelFlush =>
          flushGlobalOutputIfPossible(flush)
        case other =>
          printOrQueue(other)
      }
    }

    private def printOrFlush(
      end: ExecutionEvent.SectionEnd
    ): ZIO[Any, Nothing, Unit] =
      for {
        suiteIsPrinting <-
          reporters.attemptToGetPrintingControl(end.id, end.ancestors)
        _ <- appendToSectionContents(end.id, Chunk(end))
        _ <- getAndRemoveSectionOutput(end.id,
          sectionOutput =>

          if (suiteIsPrinting)
            (printToConsole(sectionOutput) *> reporters.relinquishPrintingControl(end.id) *> ZIO.succeed(Chunk.empty[ExecutionEvent]))
          else {
                ZIO.succeed(Chunk.empty[ExecutionEvent])
          },
          sectionOutput =>
            if(!suiteIsPrinting)
              end.ancestors.headOption match {
                case Some(parentId) =>
                  Some((parentId, sectionOutput))
                case None =>
                  // TODO If we can't find cause of failure in CI, unsafely print to console instead of failing
                  ???
//                  ZIO.dieMessage("Suite tried to send its output to a nonexistent parent. ExecutionEvent: " + end)
              }
//              Chunk.empty
            else None
          )

        _ <- reporters.relinquishPrintingControl(end.id)
      } yield ()

    private def flushGlobalOutputIfPossible(
      end: ExecutionEvent.TopLevelFlush
    ): ZIO[Any, Nothing, Unit] =
      for {
        suiteIsPrinting <-
          reporters.attemptToGetPrintingControl(SuiteId.global, List.empty)
        _ <- getAndRemoveSectionOutput(SuiteId.global,
                globalOutput =>
                if(suiteIsPrinting)
                  printToConsole(globalOutput).map(_ => Chunk.empty[ExecutionEvent])
                else
                  ZIO.succeed(globalOutput)

              )
      } yield ()

    private def printOrQueue(
      reporterEvent: ExecutionEvent
    ): ZIO[Any, Nothing, Unit] =
      for {
        _ <- appendToSectionContents(reporterEvent.id, Chunk(reporterEvent))
        suiteIsPrinting <- reporters.attemptToGetPrintingControl(
                             reporterEvent.id,
                             reporterEvent.ancestors
                           )
        _ <- getAndRemoveSectionOutput(reporterEvent.id,
                   currentOutput =>
                     if(suiteIsPrinting) {
                       printToConsole(currentOutput).map(_ => Chunk.empty)
                     } else {
                       ZIO.succeed(currentOutput)
                     }
                 )
      } yield ()

    private def printToConsole(events: Chunk[ExecutionEvent]): ZIO[Any, Nothing, Unit] =
      ZIO.foreachDiscard(events) { event =>
        executionEventPrinter.print(event)
      }

    private def appendToSectionContents(id: SuiteId, content: Chunk[ExecutionEvent]) =
      output.update { outputNow =>
        updatedWith(outputNow, id)(previousSectionOutput =>
          Some(previousSectionOutput.map(old => old ++ content).getOrElse(content))
        )
      }

    // We need this helper to run on Scala 2.11
    private def updatedWith(initial: Map[SuiteId, Chunk[ExecutionEvent]], key: SuiteId)(
      remappingFunction: Option[Chunk[ExecutionEvent]] => Option[Chunk[ExecutionEvent]]
    ): Map[SuiteId, Chunk[ExecutionEvent]] = {
      val previousValue = initial.get(key)
      val nextValue     = remappingFunction(previousValue)
      (previousValue, nextValue) match {
        case (None, None)    => initial
        case (Some(_), None) => initial - key
        case (_, Some(v))    => initial.updated(key, v)
      }
    }

  }

  object TestOutputLive {

    def make(executionEventPrinter: ExecutionEventPrinter): ZIO[Any, Nothing, TestOutput] = for {
      talkers <- TestReporters.make
      output  <- Ref.Synchronized.make[Map[SuiteId, Chunk[ExecutionEvent]]](Map.empty)
    } yield TestOutputLive(output, talkers, executionEventPrinter)

  }
}
