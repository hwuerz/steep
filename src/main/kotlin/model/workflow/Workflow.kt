package model.workflow

/**
 * A workflow
 * @param name a human-readable name
 * @param vars the variables used within the workflow
 * @param actions the actions to execute
 * @author Michel Kraemer
 */
data class Workflow(
    val api: String = "4.4.0",
    val name: String? = null,
    val vars: List<Variable> = emptyList(),
    val actions: List<Action> = emptyList()
)
