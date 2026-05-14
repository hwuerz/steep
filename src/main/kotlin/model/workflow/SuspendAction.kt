package model.workflow

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import helper.UniqueID

/**
 * A workflow action that suspends process chain execution until
 * their output variables were set by the user.
 * @param inputs a list of input parameters the user needs to
 * decide on the outputs
 * @param outputs a list of output parameters that will be defined
 * by the user during the suspend.
 * @author Hendrik M. Wuerz
 */
data class SuspendAction(
    override val id: String = UniqueID.next(),
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val inputs: List<InputParameter> = emptyList(),
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val outputs: List<OutputParameter> = emptyList(),
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    override val dependsOn: List<String> = emptyList()
) : Action
