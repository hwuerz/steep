import makeListContext from "../lib/ListContext"

// There are no continuation-specific EventBus messages yet, so this
// context only relies on the REST API for loading and does not support
// live updates (allowAdd = false, no add/update messages).
const ListContext = makeListContext()

const Provider = props => (
  <ListContext.Provider {...props} allowAdd={false} />
)

const ContinuationContext = {
  Items: ListContext.Items,
  UpdateItems: ListContext.UpdateItems,
  AddedItems: ListContext.AddedItems,
  UpdateAddedItems: ListContext.UpdateAddedItems,
  Provider
}

export default ContinuationContext
