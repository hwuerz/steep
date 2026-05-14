package model.continuation

import helper.UniqueID
import model.processchain.ArgumentVariable
import model.workflow.Action
import model.workflow.Variable

/**
 * A Continuation is a point in a workflow where execution can be
 * paused and later resumed based on user input. A continuation
 * is created by a [model.workflow.SuspendAction].
 * @param id a unique continuation identifier
 * @param inputs a list of input variable whose values are needed
 * for the decision-making during continuation
 * @param outputs a list of output variable whose values are set
 * as a result of the continuation
 * @author Hendrik M. Wuerz
 */
data class Continuation(
  val id: String = UniqueID.next(),
  val suspendActionId: String,
  val inputs: List<ContinuationParameter>,
  val outputs: List<ContinuationParameter>,
)
