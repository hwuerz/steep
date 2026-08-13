import DetailPage from "../../../components/layouts/DetailPage"
import Link from "next/link"
import { useRouter } from "next/router"
import { useContext, useEffect, useState } from "react"
import Alert from "../../../components/Alert"
import CancelModal from "../../../components/CancelModal"
import DefinitionList from "../../../components/DefinitionList"
import DefinitionListItem from "../../../components/DefinitionListItem"
import ListItemProgressBox from "../../../components/ListItemProgressBox"
import LiveDuration from "../../../components/LiveDuration"
import Modal from "../../../components/Modal"
import ModalButtons from "../../../components/ModalButtons"
import ContinuationContext from "../../../components/continuations/ContinuationContext"
import { formatDate, formatDurationTitle } from "../../../components/lib/date-time-utils"
import fetcher from "../../../components/lib/json-fetcher"
import ParameterValues from "../../../components/continuations/ParameterValues"
import styles from "./index.scss"

const ContinuationDetails = ({ id }) => {
  const continuations = useContext(ContinuationContext.Items)
  const updateContinuations = useContext(ContinuationContext.UpdateItems)
  const [error, setError] = useState()
  const [cancelModalOpen, setCancelModalOpen] = useState(false)
  const [results, setResults] = useState({})
  const [confirmModalOpen, setConfirmModalOpen] = useState(false)

  let continuation
  if (continuations.items !== undefined && continuations.items.length > 0) {
    continuation = continuations.items[0]
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

  /**
   * Handler for saving the continuation results and optionally submitting the continuation.
   * @param {boolean} submit Whether to submit the continuation when saving (setting
   * the status to "PROCESSED").
   */
  function onDoSave(submit) {
    setConfirmModalOpen(false)

    // Remove empty entries
    const cleanedResults = Object.fromEntries(
      Object.entries(results).map(([key, values]) => [
        key,
        values.filter(value => value !== "")
      ])
    )

    fetcher(`${process.env.baseUrl}/continuations/${id}`, false, {
      method: "PUT",
      body: JSON.stringify({
        results: cleanedResults,
        status: submit ? "PROCESSED" : undefined
      })
    }).then(updatedContinuation => {
      updateContinuations({ action: "set", items: [updatedContinuation] })
    }).catch(error => {
      console.error(error)
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
        readOnly={true}
      />
    )

    continuationOutputs = (
      <ParameterValues
        title="Outputs"
        parameters={results}
        setParameters={setResults}
        readOnly={continuation.status !== "OPEN"}
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
        <>
          <button
            className="btn primary"
            onClick={() => setConfirmModalOpen(true)}
          >
            Save & Submit
          </button>
          <button
            className="btn"
            onClick={() => onDoSave(false)}
          >
            Save
          </button>
        </>
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
        onConfirm={() => onDoSave(true)}
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
          <button className="btn btn-primary" onClick={() => onDoSave(true)}>
            Submit
          </button>
        </ModalButtons>
      </Modal>
      <style jsx>{styles}</style>
    </DetailPage>
  )
}

const Continuation = () => {
  const router = useRouter()
  const { id } = router.query

  return (
    <ContinuationContext.Provider allowAdd={false} pageSize={1}>
      <ContinuationDetails id={id} />
    </ContinuationContext.Provider>
  )
}

export default Continuation
