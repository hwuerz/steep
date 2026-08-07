package model.continuation

import helper.UniqueID
import java.time.Instant

/**
 * A Continuation is a point in a workflow where execution can be
 * paused and later resumed based on user input. A continuation
 * is created by a [model.workflow.SuspendAction].
 * @param id a unique continuation identifier
 * @param suspendActionId the ID of the [model.workflow.SuspendAction]
 * that created this continuation
 * @param status the current status of the continuation
 * @param inputs a list of input variable whose values are needed
 * for the decision-making during continuation
 * @param outputs a list of output variable whose values are set
 * as a result of the continuation
 * @param startTime The time when this continuation was created.
 * If not provided, the current time will be used.
 * @param endTime The time when this continuation was completed
 * @author Hendrik M. Wuerz
 */
data class Continuation(
  val id: String = UniqueID.next(),
  val suspendActionId: String,
  val status: Status = Status.REGISTERED,
  val inputs: List<ContinuationParameter>,
  val outputs: List<ContinuationParameter>,
  val startTime: Instant = Instant.now(),
  val endTime: Instant? = null,
) {
  enum class Status {
    /**
     * The continuation has been added to the registry.
     * It is waiting for user input.
     */
    REGISTERED,

    /**
     * The continuation was processed. The user provided
     * the required outputs.
     */
    PROCESSED,

    /**
     * The continuation was cancelled. There will be no
     * output defined by the user.
     */
    CANCELLED,
  }
}
