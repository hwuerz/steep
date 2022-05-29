import ListPage from "../../components/layouts/ListPage"
import ListItem from "../../components/ListItem"
import WorkflowContext from "../../components/workflows/WorkflowContext"
import { useMemo } from "react"
import workflowToProgress from "../../components/workflows/workflow-to-progress"

const FILTERS = [{
  name: "status",
  title: "Failed workflows only",
  enabledValue: "ERROR"
}, {
  name: "status",
  title: "Partially succeeded workflows only",
  enabledValue: "PARTIAL_SUCCESS"
}, {
  name: "status",
  title: "Running workflows only",
  enabledValue: "RUNNING"
}]

function WorkflowListItem({ item: workflow }) {
  return useMemo(() => {
    let href = "/workflows/[id]"
    let as = `/workflows/${workflow.id}`
    let title = workflow.name || workflow.id

    let progress = workflowToProgress(workflow)

    return <ListItem key={workflow.id} justAdded={workflow.justAdded}
        deleted={workflow.deleted} linkHref={href} linkAs={as} title={title}
        startTime={workflow.startTime} endTime={workflow.endTime}
        progress={progress} labels={workflow.requiredCapabilities} />
  }, [workflow])
}

const Workflows = () => (
  <ListPage title="Workflows" Context={WorkflowContext}
      ListItem={WorkflowListItem} subjects="workflows" path="workflows"
      filters={FILTERS} search="workflow" />
)

export default Workflows
