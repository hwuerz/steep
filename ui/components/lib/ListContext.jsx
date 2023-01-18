import EventBusContext from "./EventBusContext"
import EventBus from "@vertx/eventbus-bridge-client.js"
import { createContext, useContext, useEffect, useReducer } from "react"

function defaultItemsReducer(
  state,
  { action = "unshift", items },
  pageSize,
  shouldAddItem
) {
  switch (action) {
    case "update": {
      if (state.items !== undefined) {
        for (let item of items) {
          let i = state.items.findIndex(w => w.id === item.id)
          if (i >= 0) {
            let newItems = [...state.items]
            newItems[i] = { ...newItems[i], ...item }
            state = { ...state, items: newItems }
          }
        }
      }
      return state
    }

    case "set":
    case "unshift": {
      let itemsToAdd
      if (action === "set") {
        state = { added: 0 }
        if (items === undefined) {
          return state
        }
        itemsToAdd = items
      } else {
        itemsToAdd = []
        for (let item of items) {
          if (
            (state.items === undefined ||
              state.items.findIndex(w => w.id === item.id) < 0) &&
            (!shouldAddItem || shouldAddItem(item) === true)
          ) {
            itemsToAdd.unshift(item)
          }
        }
        state = { ...state, added: state.added + itemsToAdd.length }
      }

      let newItems = state.items || []

      if (pageSize !== undefined) {
        itemsToAdd = itemsToAdd.slice(0, pageSize)
        newItems = newItems.slice(0, pageSize - itemsToAdd.length)
      }

      return { ...state, items: [...itemsToAdd, ...newItems] }
    }

    default:
      return state
  }
}

function updateItemsReducer(reducers, pageSize, shouldAddItem) {
  return (state, action) => {
    function callReducer(state, action, i) {
      if (i === reducers.length) {
        return defaultItemsReducer(state, action, pageSize, shouldAddItem)
      }
      return reducers[i](state, action, (newState, newAction) =>
        callReducer(newState, newAction, i + 1)
      )
    }
    return callReducer(state, action, 0)
  }
}

const ProviderInternal = ({
  pageSize,
  allowAdd = true,
  shouldAddItem,
  addMessages,
  updateMessages,
  reducers = [],
  children,
  Items,
  UpdateItems
}) => {
  const [items, updateItems] = useReducer(
    updateItemsReducer(reducers, pageSize, shouldAddItem),
    { items: undefined, added: 0 }
  )
  const eventBus = useContext(EventBusContext)

  useEffect(() => {
    const registeredHandlers = {}

    if (allowAdd && eventBus !== undefined && addMessages !== undefined) {
      for (let address in addMessages) {
        let f = addMessages[address]
        let handler = (error, message) => {
          Promise.resolve(f(message.body))
            .then(items => {
              for (let item of items) {
                item.justAdded = true
              }
              updateItems({ action: "unshift", items })
            })
            .catch(err => console.error(err))
        }
        eventBus.registerHandler(address, handler)
        registeredHandlers[address] = handler
      }
    }

    return () => {
      if (eventBus !== undefined && eventBus.state === EventBus.OPEN) {
        for (let address in registeredHandlers) {
          eventBus.unregisterHandler(address, registeredHandlers[address])
        }
      }
    }
  }, [eventBus, addMessages, allowAdd])

  useEffect(() => {
    const registeredHandlers = {}

    if (eventBus !== undefined && updateMessages !== undefined) {
      for (let address in updateMessages) {
        let f = updateMessages[address]
        let handler = (error, msg) => {
          let item = f(msg.body)
          let newItems
          if (Array.isArray(item)) {
            newItems = item
          } else {
            newItems = [item]
          }
          updateItems({
            action: "update",
            items: newItems
          })
        }
        eventBus.registerHandler(address, handler)
        registeredHandlers[address] = handler
      }
    }

    return () => {
      if (eventBus !== undefined && eventBus.state === EventBus.OPEN) {
        for (let address in registeredHandlers) {
          eventBus.unregisterHandler(address, registeredHandlers[address])
        }
      }
    }
  }, [eventBus, updateMessages])

  return (
    <Items.Provider value={items}>
      <UpdateItems.Provider value={updateItems}>
        {children}
      </UpdateItems.Provider>
    </Items.Provider>
  )
}

function makeListContext() {
  const Items = createContext()
  const UpdateItems = createContext()
  const AddedItems = createContext()
  const UpdateAddedItems = createContext()

  return {
    Items,
    UpdateItems,
    AddedItems,
    UpdateAddedItems,
    Provider: props => (
      <ProviderInternal {...props} Items={Items} UpdateItems={UpdateItems} />
    )
  }
}

export default makeListContext
