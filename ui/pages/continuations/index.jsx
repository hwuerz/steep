import Link from "next/link"
import ListPage from "../../components/layouts/ListPage"
import ListItem from "../../components/ListItem"
import ContinuationContext from "../../components/continuations/ContinuationContext"
import { useMemo } from "react"
import { useRouter } from "next/router"

const FILTERS = [
  {
    name: "submissionId"
  },
  {
    name: "status",
    title: "Open continuations only",
    enabledValue: "OPEN"
  },
  {
    name: "status",
    title: "Processed continuations only",
    enabledValue: "PROCESSED"
  },
  {
    name: "status",
    title: "Cancelled continuations only",
    enabledValue: "CANCELLED"
  }
]

function ContinuationListItem({ item: continuation }) {
  return useMemo(() => {
    let href = "/continuations/[id]"
    let as = `/continuations/${continuation.id}`

    let progress = {
      status: continuation.status
    }

    return (
      <ListItem
        title={continuation.id}
        linkHref={href}
        linkAs={as}
        deleted={false}
        justAdded={false}
        justLeft={false}
        startTime={continuation.startTime}
        endTime={continuation.endTime}
        progress={progress}
        labels={continuation.outputs?.map(o => o.id) || []}
      />
    )
  }, [continuation])
}

const Continuations = () => {
  const router = useRouter()

  let breadcrumbs
  if (router.query.submissionId !== undefined) {
    let sid = router.query.submissionId
    breadcrumbs = [
      <Link href="/workflows" key="workflows">
        Workflows
      </Link>,
      <Link href="/workflows/[id]" as={`/workflows/${sid}`} key={sid}>
        {sid}
      </Link>,
      "Continuations"
    ]
  }

  return (
    <ListPage
      title="Continuations"
      Context={ContinuationContext}
      ListItem={ContinuationListItem}
      subjects="continuations"
      path="continuations"
      filters={FILTERS}
      breadcrumbs={breadcrumbs}
    />
  )
}

export default Continuations
