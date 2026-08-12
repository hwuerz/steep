import makeListContext from "../lib/ListContext"
import { produce } from "immer"

import {
    CONTINUATIONS_ADDED,
    CONTINUATION_STATUS_CHANGED,
    CONTINUATION_ALL_STATUS_CHANGED,
    CONTINUATION_RESULTS_CHANGED
} from "../lib/EventBusMessages"

const ADD_MESSAGES = {
  [CONTINUATIONS_ADDED]: body => {
    for (let continuation of body.continuations) {
      continuation.submissionId = body.submissionId
    }
    return body.continuations
  }
}

const UPDATE_MESSAGES = {
  [CONTINUATION_STATUS_CHANGED]: body => ({
    id: body.continuationId,
    status: body.status,
    submissionId: body.submissionId
  }),
  [CONTINUATION_ALL_STATUS_CHANGED]: body => ({
    currentStatus: body.currentStatus,
    status: body.newStatus,
    submissionId: body.submissionId
  }),
  [CONTINUATION_RESULTS_CHANGED]: body => ({
    id: body.continuationId,
    results: body.results
  })
}

function reducer(state, { action, items }, next) {
  if (action === "update" && items.length > 0 && state.items !== undefined) {
    if (items[0].id === undefined && items[0].submissionId !== undefined) {
      // update all continuations of a submission
      return produce(state, draft => {
        for (let item of items) {
          for (let c of draft.items) {
            if (c.submissionId === item.submissionId) {
              if (
                item.currentStatus !== undefined &&
                c.status === item.currentStatus
              ) {
                c.status = item.status
              }
            }
          }
        }
      })
    }
  }

  return next(state, { action, items })
}

const ListContext = makeListContext()

const Provider = props => {
  let reducers = [...(props.reducers || []), reducer]
  return (
    <ListContext.Provider
      {...props}
      addMessages={ADD_MESSAGES}
      updateMessages={UPDATE_MESSAGES}
      reducers={reducers}
    />
  )
}

const ContinuationContext = {
  Items: ListContext.Items,
  UpdateItems: ListContext.UpdateItems,
  AddedItems: ListContext.AddedItems,
  UpdateAddedItems: ListContext.UpdateAddedItems,
  Provider
}

export default ContinuationContext
