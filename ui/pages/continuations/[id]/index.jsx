import { useRouter } from "next/router"
import ContinuationDetails from "../../../components/continuations/ContinuationDetails"
import ContinuationContext from "../../../components/continuations/ContinuationContext"

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
