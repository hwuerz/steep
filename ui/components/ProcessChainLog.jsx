import Alert from "./Alert"
import Code from "./Code"
import Tooltip from "./Tooltip"
import fetcher from "./lib/json-fetcher"
import EventBusContext from "./lib/EventBusContext"
import { LOGS_PROCESSCHAINS_PREFIX } from "./lib/EventBusMessages"
import EventBus from "vertx3-eventbus-client"
import { useCallback, useContext, useEffect, useRef, useState } from "react"
import { throttle } from "lodash"
import { ChevronsDown } from "react-feather"
import parseRangeHeader from "parse-content-range-header"
import classNames from "classnames"
import styles from "./ProcessChainLog.scss"

const ProcessChainLog = ({ id, onError }) => {
  const ref = useRef()
  const nextEntryKey = useRef(0)
  const [contents, setContents] = useState([])
  const [liveContents, setLiveContents] = useState([])
  const [error, doSetError] = useState()
  const [loadingVisible, setLoadingVisible] = useState(false)
  const [followButtonVisible, setFollowButtonVisible] = useState(false)
  const eventBus = useContext(EventBusContext)

  const scrollToEnd = useCallback(() => {
    setTimeout(() => {
      if (ref.current) {
        ref.current.scrollTop = ref.current.scrollHeight
      }
    }, 0)
  }, [])

  const onFollowClick = useCallback(() => {
    scrollToEnd()
  }, [scrollToEnd])

  const setError = useCallback((error) => {
    doSetError(error)
    if (onError) {
      onError(error)
    }
  }, [onError])

  useEffect(() => {
    let codeRef = ref.current
    let contentRange = undefined
    let scrollLockedAtEnd = true

    function isScrollAtEnd() {
      let lineHeight = parseInt(window.getComputedStyle(codeRef).lineHeight)
      let threshold = codeRef.scrollHeight - codeRef.clientHeight - lineHeight
      return threshold < codeRef.scrollTop
    }

    const onScroll = throttle(() => {
      let scrollAtEnd = isScrollAtEnd()
      setFollowButtonVisible(!scrollAtEnd)
      scrollLockedAtEnd = scrollAtEnd

      if (codeRef.scrollTop === 0 && contentRange !== undefined && contentRange.first > 0) {
        setLoadingVisible(true)
        load(true)
      }
    }, 100)

    if (id === undefined) {
      return
    }

    async function handleResponse(r) {
      let body = await r.text()
      if (r.status === 404) {
        return undefined
      } else if (r.status === 200) {
        contentRange = parseRangeHeader(r.headers.get("x-content-range"))
        if (contentRange.first !== 0) {
          // strip first line (which is most likely incomplete)
          let lineEnd = body.indexOf("\n") + 1
          body = body.substring(lineEnd)
          contentRange.first += lineEnd
        }
      } else if (r.status !== 200) {
        throw new Error(body)
      }
      return body
    }

    function load(more = false) {
      let bytes = -2000000 // load last 2 MB
      if (more) {
        bytes = Math.max(0, contentRange.first + bytes) + "-" + (contentRange.first - 1)
      }

      let options = {
        headers: {
          "accept": "text/plain",
          "x-range": `bytes=${bytes}`
        }
      }

      let loadTimer
      if (!more) {
        loadTimer = setTimeout(() => {
          setContents([""])
          setLoadingVisible(true)
        }, 50)
      }

      fetcher(`${process.env.baseUrl}/logs/processchains/${id}`, false, options, handleResponse)
          .then(log => {
            if (loadTimer !== undefined) {
              clearTimeout(loadTimer)
            }
            if (log === undefined) {
              setError(<Alert error>
                  <p>Unable to find process chain log</p>
                  <p>This can have various reasons:</p>
                  <ul>
                    <li>The process chain has not produced any output (yet)</li>
                    <li>The agent that has executed the process chain is not available anymore</li>
                    <li>Process chain logging is disabled in Steep&rsquo;s configuration</li>
                  </ul>
                </Alert>)
              setContents([])
            } else {
              if (!more) {
                setContents([{
                  key: nextEntryKey.current++,
                  value: log
                }])
                scrollToEnd()
                setFollowButtonVisible(false)
                setLoadingVisible(false)
              } else {
                let oldScrollBottom = codeRef.scrollHeight - codeRef.scrollTop
                setContents(old => [{
                  key: nextEntryKey.current++,
                  value: log
                }, ...old])
                setTimeout(() => {
                  codeRef.scrollTop = codeRef.scrollHeight - oldScrollBottom
                  setLoadingVisible(false)
                }, 0)
              }
            }
          })
          .catch(err => {
            if (loadTimer !== undefined) {
              clearTimeout(loadTimer)
            }
            console.log(err)
            setError(<Alert error>Could not load process chain</Alert>)
            setContents([])
          })
    }

    function onNewLogLine(err, msg) {
      setLiveContents(oldContents => [...oldContents, {
        key: nextEntryKey.current++,
        value: msg.body
      }])
      setError(undefined)
      if (scrollLockedAtEnd) {
        scrollToEnd()
      }
    }

    // register event bus consumer receiving live log lines
    let address = LOGS_PROCESSCHAINS_PREFIX + id

    if (eventBus !== undefined) {
      eventBus.registerHandler(address, onNewLogLine)
    }

    // register scroll listener to control visibility of "follow" button
    codeRef.addEventListener("scroll", onScroll)

    load()

    return () => {
      if (eventBus !== undefined && eventBus.state === EventBus.OPEN) {
        eventBus.unregisterHandler(address, onNewLogLine)
      }
      codeRef.removeEventListener("scroll", onScroll)
    }
  }, [id, eventBus, scrollToEnd, setError])

  let codeVisible = contents.length > 0 || liveContents.length > 0

  return (<>
    {codeVisible || error}
    <div className={classNames("processchainlog", { visible: codeVisible })}>
      <Code lang="log" ref={ref}>{[...contents, ...liveContents]}</Code>
      <div className={classNames("follow-button", { visible: followButtonVisible })} onClick={onFollowClick}>
        <Tooltip title="Follow">
          <ChevronsDown className="feather" />
        </Tooltip>
      </div>
      <div className={classNames("loading", { visible: loadingVisible })}>
        <div className="sk-flow">
          <div className="sk-flow-dot"></div>
          <div className="sk-flow-dot"></div>
          <div className="sk-flow-dot"></div>
        </div>
      </div>
    </div>
    <style jsx>{styles}</style>
  </>)
}

export default ProcessChainLog
