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
 * @param {*} setParameters Function to update the parameter values.
 * @param {boolean} readOnly Whether the parameter values are read-only.
 * @returns {JSX.Element}
 */
const ParameterValues = ({ title, parameters, setParameters, readOnly }) => {

  function onChangeParameterValues(parameterId, index, value) {

    /** @type {string[]} */
    const currentValues = parameters[parameterId]

    // Check if this is a new value
    if (index === currentValues.length) {
      currentValues.push(value)
    } else {
      currentValues[index] = value
    }

    const updatedParameterValues = {
      ...parameters,
      [parameterId]: currentValues
    }
    setParameters?.(updatedParameterValues)
  }

  return (
    <>
      <div className="parameter-values">
        <h2>
          {title}
        </h2>
        {parameters && Object.keys(parameters).length > 0 ? (
          <form onSubmit={event => event.preventDefault()}>
            {Object.entries(parameters).map(([parameterId, parameterValues]) => {
              const lines = [
                ...parameterValues,
                // Add an empty input field for new values
                ...(!readOnly ? [""] : [])
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
                          readOnly={readOnly}
                          value={parameterValue}
                          onChange={event =>
                            onChangeParameterValues(parameterId, i, event.target.value)
                          }
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
          <>No parameters defined</>
        )}
      </div>
      <style jsx>{styles}</style>
    </>
  )
}

export default ParameterValues
