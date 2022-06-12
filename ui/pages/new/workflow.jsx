import Alert from "../../components/Alert"
import DetailPage from "../../components/layouts/DetailPage"
import classNames from "classnames"
import fetcher from "../../components/lib/json-fetcher"
import submissionToSource from "../../components/lib/submission-source"
import Editor, { useMonaco } from "@monaco-editor/react"
import { useEffect, useRef, useState } from "react"
import { Circle } from "react-feather"
import { useRouter } from "next/router"
import styles from "./workflow.scss"

const Submit = () => {
  const [ready, setReady] = useState(false)
  const [value, setValue] = useState()
  const [error, setError] = useState()
  const router = useRouter()

  const editorRef = useRef()
  const monaco = useMonaco()

  useEffect(() => {
    if (monaco) {
      monaco.editor.defineTheme("steep", {
        base: "vs-dark",
        inherit: true,
        rules: [
          { token: "comment", foreground: "7f9f7f" },
          { token: "keyword", foreground: "e3ceab" },
          { token: "number", foreground: "8cd0d3" },
          { token: "type", foreground: "ffffff" },
          { token: "operators", foreground: "ffffff" },
          { token: "string", foreground: "cc9393" }
        ],
        colors: {
          "editor.background": "#495057",
          "editor.foreground": "#ffffff",
          "editor.lineHighlightBorder": "#424950",
          "editorLineNumber.foreground": "#6c757d",
          "editor.selectionBackground": "#6590cc",
          "editor.inactiveSelectionBackground": "#5580bb",
          "scrollbar.shadow": "#495057"
        }
      })
      monaco.editor.setTheme("steep")
    }
  }, [monaco])

  useEffect(() => {
    if (router.query.from !== undefined) {
      fetcher(`${process.env.baseUrl}/workflows/${router.query.from}`).then(response => {
        setValue(submissionToSource(response).yaml)
        setReady(true)
        if (editorRef.current && monaco) {
          editorRef.current.setSelection(new monaco.Selection(0, 0, 0, 0))
        }
      }).catch(error => {
        setError(error.message)
        console.log(error)
      })
    }
  }, [router.query.from, monaco])

  function handleEditorOnMount(editor) {
    editorRef.current = editor
    editor.focus()
  }

  function handleEditorChange(value) {
    setValue(value)
    setReady(value !== undefined && value.length > 0)
  }

  function onSubmit() {
    if (value === undefined || value.length === 0) {
      return
    }

    setReady(false)

    fetcher(`${process.env.baseUrl}/workflows/`, false, {
      method: "POST",
      body: value
    }).then(response => {
      router.push("/workflows/[id]", `/workflows/${response.id}`)
    }).catch(error => {
      setError(error.message)
      console.log(error)
      setReady(true)
    })
  }

  function onCancel() {
    router.back()
  }

  const loading = (<>
    <div className="loading"><Circle /></div>
    <style jsx>{styles}</style>
  </>)

  const options = {
    // see main.scss
    fontFamily: "SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace",
    fontSize: "16.74px",
    lineHeight: "28.458px",

    automaticLayout: true,
    lineNumbers: "on",
    minimap: {
      enabled: false
    },
    renderLineHighlight: false,
    scrollBeyondLastLine: false
  }

  return (
    <DetailPage title="New workflow" footerNoTopMargin={true}>
      <div className={classNames("editor-container", { "with-error": error !== undefined })}>
        {error && <div>
          <Alert error>{error}</Alert>
        </div>}
        <div className="editor-main">
          <div className="editor-wrapper">
            <Editor width="100%" height="100%" language="yaml" value={value}
              onMount={handleEditorOnMount} onChange={handleEditorChange}
              theme="steep" loading={loading} options={options} />
          </div>
          <div className="buttons">
            <button className="btn primary submit-button" disabled={!ready}
              onClick={onSubmit}>Submit</button>
            <button className="btn cancel-button" onClick={onCancel}>Cancel</button>
          </div>
        </div>
      </div>
      <style jsx>{styles}</style>
    </DetailPage>
  )
}

export default Submit
