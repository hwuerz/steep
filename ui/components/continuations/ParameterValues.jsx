import { useEffect, useState } from "react"
import {
  PencilLine,
  LoaderCircle
} from "lucide-react"
import fetcher from "../lib/json-fetcher"
import styles from "./ParameterValues.scss"

/**
 * @param {string} title The heading of the section
 * @param {Object.<string, string[]>} parameters An object with the parameter ID as
 * key and an array of its values as the object value.
 * @param {boolean} isEditable
 * @param {string} updateUrl The URL to send the updated parameter values to.
 * @param {function(Object.<string, string[]>): void} onChange
 * @returns {JSX.Element}
 */
const ParameterValues = ({ title, parameters, isEditable, updateUrl, onChange }) => {
  // The localParameterValues are used to cache changed values before sending
  // them to the server.
  const [localParameterValues, setLocalParameterValues] = useState({})

  // Flag to indicate if there are unsaved changes in the localParameterValues
  const [unsavedParameterValues, setUnsavedParameterValues] = useState(false)

  // Flag to indicate if a request to save changes is currently pending
  const [pendingRequest, setPendingRequest] = useState(false)

  useEffect(() => {
    if (!parameters) {
      return
    }

    setLocalParameterValues(parameters)
  }, [parameters])

  function onChangeParameterValues(parameterId, index, value) {
    setUnsavedParameterValues(true)

    /** @type {string[]} */
    const currentValues = localParameterValues[parameterId]

    // Check if this is a new value
    if (index === currentValues.length) {
      currentValues.push(value)
    } else {
      currentValues[index] = value
    }

    const updatedParameterValues = {
      ...localParameterValues,
      [parameterId]: currentValues
    }
    setLocalParameterValues(updatedParameterValues)
    onChange?.(updatedParameterValues)
  }

  function saveParameterValues() {
    setPendingRequest(true)
    // Remove empty entries
    const cleanedParameterValues = Object.fromEntries(
      Object.entries(localParameterValues).map(([key, values]) => [
        key,
        values.filter(value => value !== "")
      ])
    )
    setLocalParameterValues(cleanedParameterValues)

    fetcher(updateUrl, false, {
      method: "PUT",
      body: JSON.stringify({ results: cleanedParameterValues })
    }).then(() => {
      setUnsavedParameterValues(false)
    }).catch(error => {
      // TODO: Show error to user
      console.error(error)
    }).finally(() => {
      setPendingRequest(false)
    })
  }

  function saveParameterValuesIfNeeded(event) {
    if (event.currentTarget.form.contains(event.relatedTarget)) {
      // If we are still in the same form, don't save yet.
      return
    }
    if (event.relatedTarget?.hasAttribute("data-continuation-submit")) {
      // If the user clicked the submit button, data will be saved anyway.
      setUnsavedParameterValues(false)
      return
    }

    if (unsavedParameterValues) {
      saveParameterValues()
    }
  }

  return (
    <>
      <div className="parameter-values">
        <h2>
          {title}
          <span className="unsaved-indicator">
            {unsavedParameterValues && !pendingRequest && (
              <PencilLine>
                <title>Unsaved changes</title>
              </PencilLine>
            )}
            {pendingRequest && (
              <span className="spinner" title="Saving changes...">
                <LoaderCircle />
              </span>
            )}
          </span>
        </h2>
        {localParameterValues && Object.keys(localParameterValues).length > 0 ? (
          <form onSubmit={event => event.preventDefault()}>
            {Object.entries(localParameterValues).map(([parameterId, parameterValues]) => {
              const lines = [
                ...parameterValues,
                // Add an empty input field for new values
                ...(isEditable ? [""] : [])
              ]
              return (
                <div key={parameterId} className="value-item">
                  <div className="value-item-left">{parameterId}</div>
                  <div className="value-item-right">
                    {lines.length > 0 ? (
                      lines.map((parameterValue, i) => (
                        <input
                          key={i}
                          type="text"
                          readOnly={!isEditable || pendingRequest}
                          value={parameterValue}
                          onChange={event =>
                            onChangeParameterValues(parameterId, i, event.target.value)
                          }
                          onBlur={saveParameterValuesIfNeeded}
                        />
                      ))
                    ) : (
                      <>No values defined</>
                    )}
                  </div>
                </div>
              )
            })}
          </form>
        ) : (
          <>No Parameters defined</>
        )}
      </div>
      <style jsx>{styles}</style>
    </>
  )
}

export default ParameterValues
