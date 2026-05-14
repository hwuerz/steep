package model.continuation

import helper.UniqueID
import model.processchain.Argument
import model.processchain.ArgumentVariable

/**
 * Continuation parameter
 * The inputs and outputs used in a continuation. Inputs are parameters
 * whose values are needed for the decision-making during continuation,
 * while outputs are parameters whose values are set as a result of the
 * continuation.
 * @param id a unique parameter identifier
 * @param value Can be a list of values or a single value. Until this
 * continuation is fulfilled, the value will be null for outputs.
 * @param type parameter type
 * @author Hendrik M. Wuerz
 */
data class ContinuationParameter(
    val id: String,
    val value: Any?,
    val type: Argument.Type,
)
