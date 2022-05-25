import Alert from "../../components/Alert"
import Label from "../../components/Label"
import Link from "next/link"
import { Link as LinkIcon, Send } from "react-feather"
import styles from "./ResultsRow.scss"

// coalesce the given positions (i.e. recursively merge overlapping ranges)
function coalescePositions(positions) {
  let result = []
  let anymerged = false
  for (let p of positions) {
    let merged = false
    for (let r of result) {
      if ((p[0] >= r[0] && p[0] <= r[1]) || (p[1] >= r[0] && p[1] <= r[1])) {
        r[0] = Math.min(r[0], p[0])
        r[1] = Math.max(r[1], p[1])
        merged = true
        anymerged = true
        break
      }
    }
    if (!merged) {
      result.push(p)
    }
  }
  if (anymerged) {
    return coalescePositions(result)
  }
  return result
}

// sort the given positions according to their start
function sortPositions(positions) {
  positions.sort((a, b) => a[0] - b[0])
  return positions
}

// coalesce and sort positions
function normalizePositions(positions) {
  return sortPositions(coalescePositions(positions))
}

function highlight(str, positions) {
  let lastend = 0
  let tokens = []
  for (let i in positions) {
    let p = positions[i]
    if (p[0] > lastend) {
      tokens.push(str.substring(lastend, p[0]))
    }
    tokens.push(
      <span key={i} className="results-highlight">
        {str.substring(p[0], p[1])}
        <style jsx>{styles}</style>
      </span>
    )
    lastend = p[1]
  }
  if (lastend < str.length) {
    tokens.push(str.substring(lastend, str.length))
  }
  return <span>{tokens}</span>
}

function highlightMatch(match) {
  let positions = []
  for (let tm of match.termMatches) {
    for (let i of tm.indices) {
      positions.push([i, i + tm.term.length])
    }
  }
  positions = normalizePositions(positions)

  return highlight(match.fragment, positions)
}

const ResultsRow = ({ result }) => {
  let idMatch = result.matches.find(m => m.locator === "id")
  let id = idMatch ? highlightMatch(idMatch) : result.id

  let nameMatch = result.matches.find(m => m.locator === "name")
  let name = nameMatch ? highlightMatch(nameMatch) : result.name

  let title
  if (name) {
    title = <>{name} ({id})</>
  } else {
    title = id
  }

  let type
  let href
  switch (result.type) {
    case "processChain":
      type = "Process chain"
      href = `/processchains/${result.id}`
      break
    case "workflow":
      type = "Workflow"
      href = `/workflows/${result.id}`
      break
  }

  let labels = []
  if (result.requiredCapabilities !== undefined) {
    for (let rc of result.requiredCapabilities) {
      let rcMatch = result.matches.find(m => m.locator === "requiredCapabilities" &&
          m.fragment === rc)
      let hrc = rcMatch ? highlightMatch(rcMatch) : rc
      labels.push(hrc)
    }
  }

  let errorMatch = result.matches.find(m => m.locator === "errorMessage")
  let errorMessage
  if (errorMatch) {
    errorMessage = <div className="results-row-error-message">
      <Alert error small>{highlightMatch(errorMatch)}</Alert>
      <style jsx>{styles}</style>
    </div>
  }

  let sourceMatch = result.matches.find(m => m.locator === "source")
  let source
  if (sourceMatch) {
    source = <div className="results-row-source">
      <pre><code className="hljs language-json">{highlightMatch(sourceMatch)}</code></pre>
      <style jsx>{styles}</style>
    </div>
  }

  return (<>
    <div className="results-row">
      <div className="results-row-title">
        <span className="results-row-icon">
          {result.type === "workflow" && <Send size="1.2rem" />}
          {result.type === "processChain" && <LinkIcon size="1.2rem" />}
        </span>
        <Link href={href}><a>{title}</a></Link>{labels.length > 0 && <>&ensp;</>}
        {labels.map((l, i) => <Label key={i} small>{l}</Label>)}
      </div>
      {errorMessage}
      {source}
      <div className="results-row-info">
        {type && <>{type}</>}
      </div>
    </div>
    <style jsx>{styles}</style>
  </>)
}

export default ResultsRow