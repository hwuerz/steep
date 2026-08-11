import DetailPage from "../layouts/DetailPage"
import Link from "next/link"
import { useContext, useEffect, useState } from "react"
import Alert from "../Alert"
import CancelModal from "../CancelModal"
import DefinitionList from "../DefinitionList"
import DefinitionListItem from "../DefinitionListItem"
import ListItemProgressBox from "../ListItemProgressBox"
import LiveDuration from "../LiveDuration"
import ContinuationContext from "../continuations/ContinuationContext"
import EventBusContext from "../lib/EventBusContext"
import { formatDate, formatDurationTitle } from "../lib/date-time-utils"
import fetcher from "../lib/json-fetcher"
import ParameterValues from "./ParameterValues"
import styles from "./ContinuationDetails.scss"
import Modal from "../Modal"
import ModalButtons from "../ModalButtons"

const ContinuationDetails = ({ id }) => {
  const continuations = useContext(ContinuationContext.Items)
  const updateContinuations = useContext(ContinuationContext.UpdateItems)
  const eventBus = useContext(EventBusContext)
  const [error, setError] = useState()
  const [cancelModalOpen, setCancelModalOpen] = useState()
  const [results, setResults] = useState({})
  const [confirmModalOpen, setConfirmModalOpen] = useState(false)
  const [submitPending, setSubmitPending] = useState(false)

  let continuation
  if (continuations.items !== undefined && continuations.items.length > 0) {
    continuation = continuations.items[0]
  } else {
    continuation = undefined
  }

  useEffect(() => {
    if (!continuation) {
      return
    }

    setResults(Object.fromEntries(
      continuation.outputs.map(output => [
        output.id,
        // Prepare an empty array for each output if none exists yet.
        continuation.results?.[output.id] || []
      ])
    ))
  }, [continuation])

  useEffect(() => {
    if (!id) {
      return
    }

    fetcher(`${process.env.baseUrl}/continuations/${id}`)
      .then(continuation => updateContinuations({ action: "set", items: [continuation] }))
      .catch(err => {
        console.log(err)
        setError(<Alert error>Could not load continuation</Alert>)
      })
  }, [id, updateContinuations])

  function onDoCancel() {
    setCancelModalOpen(false)
    fetcher(`${process.env.baseUrl}/continuations/${id}`, false, {
      method: "PUT",
      body: JSON.stringify({
        status: "CANCELLED"
      })
    }).catch(error => {
      console.error(error)
    })
  }

  function onSubmit() {
    const cleanedResults = Object.fromEntries(
      Object.entries(results).map(([key, values]) => [
        key,
        values.filter(value => value !== "")
      ])
    )

    setSubmitPending(true)
    fetcher(`${process.env.baseUrl}/continuations/${id}`, false, {
      method: "PUT",
      body: JSON.stringify({ results: cleanedResults, status: "PROCESSED" })
    }).then(updatedContinuation => {
      updateContinuations({ action: "set", items: [updatedContinuation] })
    }).catch(error => {
      console.error(error)
    }).finally(() => {
      setSubmitPending(false)
    })
  }

  let breadcrumbs
  let title
  let continuationMetadata
  let menu
  let continuationInputs
  let continuationOutputs

  if (continuation !== undefined) {
    title = continuation.id
    breadcrumbs = [
      <Link href="/workflows" key="workflows">
        Workflows
      </Link>,
      <Link
        href="/workflows/[id]"
        as={`/workflows/${continuation.submissionId}`}
        key={continuation.submissionId}
      >
        {continuation.submissionId}
      </Link>,
      <Link
        href={{
          pathname: "/continuations",
          query: {
            submissionId: continuation.submissionId
          }
        }}
        key="continuations"
      >
        Continuations
      </Link>,
      continuation.id
    ]

    let menuItems = []
    if (continuation.status === "OPEN") {
      menuItems.push(
        <li key="cancel" onClick={() => setCancelModalOpen(true)}>
          Cancel
        </li>
      )
    }
    if (menuItems.length > 0) {
      menu = <ul>{menuItems}</ul>
    }

    let progress = {
      status: continuation.status
    }

    continuationMetadata = (
      <>
        <div className="detail-header">
          <div className="left-two-columns">
            <div className="detail-header-left">
              <DefinitionList>
                <DefinitionListItem title="Start time">
                  {continuation.startTime ? (
                    formatDate(continuation.startTime)
                  ) : (
                    <>&ndash;</>
                  )}
                </DefinitionListItem>
                <DefinitionListItem title="End time">
                  {continuation.endTime ? (
                    formatDate(continuation.endTime)
                  ) : (
                    <>&ndash;</>
                  )}
                </DefinitionListItem>
                <DefinitionListItem title="Time elapsed">
                  {continuation.startTime && continuation.endTime ? (
                    formatDurationTitle(
                      continuation.startTime,
                      continuation.endTime
                    )
                  ) : continuation.startTime ? (
                    <LiveDuration startTime={continuation.startTime} />
                  ) : (
                    <>&ndash;</>
                  )}
                </DefinitionListItem>
              </DefinitionList>
            </div>
          </div>
          <div className="detail-header-right">
            <ListItemProgressBox progress={progress} />
          </div>
        </div>
        <style jsx>{styles}</style>
      </>
    )

    continuationInputs = (
      <ParameterValues
        title="Inputs"
        parameters={Object.fromEntries(
          continuation.inputs.map(input => [
            input.id,
            Array.isArray(input.value) ? input.value : [input.value]
          ])
        )}
        isEditable={false}
      />
    )

    continuationOutputs = (
      <ParameterValues
        title="Outputs"
        parameters={results}
        isEditable={continuation.status === "OPEN"}
        updateUrl={`${process.env.baseUrl}/continuations/${continuation.id}`}
        onChange={setResults}
      />
    )
  }

  let menus = []
  if (menu !== undefined) {
    menus.push({
      title: "Actions",
      menu
    })
  }

  return (
    <DetailPage
      breadcrumbs={breadcrumbs}
      title={title}
      menus={menus}
    >
      {continuationMetadata || error}
      {continuationInputs}
      {continuationOutputs}
      {continuation?.status === "OPEN" && (
        <button
          data-continuation-submit
          className="btn primary"
          disabled={submitPending}
          onClick={() => setConfirmModalOpen(true)}
        >
          Submit
        </button>
      )}

      <CancelModal
        isOpen={cancelModalOpen}
        contentLabel="Cancel modal"
        onRequestClose={() => setCancelModalOpen(false)}
        title="Cancel continuation"
        onConfirm={onDoCancel}
        onDeny={() => setCancelModalOpen(false)}
      >
        <p>Are you sure you want to cancel this continuation?</p>
      </CancelModal>

      <Modal
        isOpen={confirmModalOpen}
        contentLabel="Confirm modal"
        onRequestClose={() => setConfirmModalOpen(false)}
        title="Submit continuation"
        onConfirm={onSubmit}
        onDeny={() => setConfirmModalOpen(false)}
      >
        <p>
          Are you sure you want to submit this continuation? Workflow execution
          will continue with the provided output values.
        </p>
        <ModalButtons>
          <button className="btn" onClick={() => setConfirmModalOpen(false)}>
            Cancel
          </button>
          <button className="btn btn-primary" onClick={onSubmit}>
            Submit
          </button>
        </ModalButtons>
      </Modal>
    </DetailPage>
  )
}

export default ContinuationDetails
