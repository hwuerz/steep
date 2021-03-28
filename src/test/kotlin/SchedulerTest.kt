import agent.Agent
import agent.AgentRegistry
import agent.AgentRegistryFactory
import db.SubmissionRegistry
import db.SubmissionRegistry.ProcessChainStatus.ERROR
import db.SubmissionRegistry.ProcessChainStatus.REGISTERED
import db.SubmissionRegistry.ProcessChainStatus.RUNNING
import db.SubmissionRegistry.ProcessChainStatus.SUCCESS
import db.SubmissionRegistryFactory
import helper.UniqueID
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.deploymentOptionsOf
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import kotlinx.coroutines.delay
import model.processchain.ProcessChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.IllegalStateException
import kotlin.math.max

/**
 * Tests for the [Scheduler]
 * @author Michel Kraemer
 */
@ExtendWith(VertxExtension::class)
class SchedulerTest {
  private lateinit var submissionRegistry: SubmissionRegistry
  private lateinit var agentRegistry: AgentRegistry

  private var expectedRequiredCapabilities = listOf<Pair<Collection<String>, Long>>()

  @BeforeEach
  fun setUp(vertx: Vertx, ctx: VertxTestContext) {
    expectedRequiredCapabilities = listOf(emptyList<String>() to 13L)

    // mock submission registry
    submissionRegistry = mockk()
    mockkObject(SubmissionRegistryFactory)
    every { SubmissionRegistryFactory.create(any()) } returns submissionRegistry
    coEvery { submissionRegistry.findProcessChainRequiredCapabilities(any()) } answers {
      expectedRequiredCapabilities.map { it.first }
    }
    val rcsSlot = slot<Collection<String>>()
    coEvery { submissionRegistry.countProcessChains(null, REGISTERED, capture(rcsSlot)) } answers {
      expectedRequiredCapabilities.find {
        it.first == rcsSlot.captured
      }?.second ?: throw IllegalStateException(
          "Invalid required capabilities set `${rcsSlot.captured}'")
    }
    coEvery { submissionRegistry.close() } just Runs

    // mock agent registry
    agentRegistry = mockk()
    mockkObject(AgentRegistryFactory)
    every { AgentRegistryFactory.create(any()) } returns agentRegistry

    // deploy verticle under test
    val options = deploymentOptionsOf(config = json {
      obj(
          ConfigConstants.AGENT_ID to UniqueID.next()
      )
    })
    vertx.deployVerticle(Scheduler::class.qualifiedName, options, ctx.completing())
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  /**
   * Runs a simple test: schedules `nProcessChains` process chains and provides
   * `nAgents` mock agents to execute the process chains. Each agent needs
   * 1 second to execute a process chain. The method waits until all process
   * chains have been executed successfully.
   * @param nProcessChains the number of process chains to schedule
   * @param nAgents the number of mock agents to create
   * @param vertx the Vert.x instance
   * @param ctx the test context
   */
  private fun testSimple(nProcessChains: Int, nAgents: Int, vertx: Vertx, ctx: VertxTestContext) {
    val remainingExpectedRequiredCapabilities = expectedRequiredCapabilities.toMutableList()

    // mock agents
    val allAgents = (1..nAgents).map { n ->
      val a = mockk<Agent>()
      every { a.id } returns "Mock agent $n"
      a
    }

    val availableAgents = allAgents.toMutableList()
    for (agent in allAgents) {
      val pcSlot = slot<ProcessChain>()
      coEvery { agent.execute(capture(pcSlot)) } coAnswers {
        delay(1000) // pretend it takes 1 second to execute the process chain
        mapOf("ARG1" to listOf("output-${pcSlot.captured.id}"))
      }
    }

    val slotRequiredCapabilities = slot<List<Pair<Collection<String>, Long>>>()
    coEvery { agentRegistry.selectCandidates(capture(slotRequiredCapabilities)) } answers {
      ctx.verify {
        if (slotRequiredCapabilities.captured.isNotEmpty()) {
          assertThat(slotRequiredCapabilities.captured)
              .isEqualTo(remainingExpectedRequiredCapabilities)
        }
      }
      slotRequiredCapabilities.captured.mapIndexedNotNull { i, rc ->
        if (i >= availableAgents.size) {
          null
        } else {
          // reduce number of process chains per expected required capability set
          for ((j, rerc) in remainingExpectedRequiredCapabilities.withIndex()) {
            if (rerc.first == rc.first) {
              remainingExpectedRequiredCapabilities[j] = rerc.copy(
                  second = max(0, rerc.second - 1))
            }
          }
          Pair(rc.first, availableAgents[i].id)
        }
      }
    }

    val slotAgentAddress = slot<String>()
    coEvery { agentRegistry.tryAllocate(capture(slotAgentAddress), any()) } answers {
      val agent = availableAgents.find { it.id == slotAgentAddress.captured }
      if (agent != null) {
        availableAgents.remove(agent)
      }
      agent
    }

    val slotAgent = slot<Agent>()
    coEvery { agentRegistry.deallocate(capture(slotAgent)) } answers {
      // put back agent
      availableAgents.add(slotAgent.captured)
    }

    // mock submission registry
    val allPcs = (1..nProcessChains).map { ProcessChain() }
    val registeredPcs = allPcs.toMutableList()
    for (pc in allPcs) {
      // add running process chain to list of registered process chains again
      coEvery { submissionRegistry.setProcessChainStatus(pc.id, REGISTERED) } answers {
        ctx.verify {
          assertThat(registeredPcs).doesNotContain(pc)
        }
        registeredPcs.add(0, pc)
      }

      // register mock for start and end time
      coEvery { submissionRegistry.setProcessChainStartTime(pc.id, any()) } just Runs
      coEvery { submissionRegistry.setProcessChainEndTime(pc.id, any()) } just Runs

      // register mock for results
      coEvery { submissionRegistry.setProcessChainResults(pc.id,
          mapOf("ARG1" to listOf("output-${pc.id}"))) } just Runs
    }

    for (pc in allPcs) {
      // register mocks for all successful process chains
      coEvery { submissionRegistry.setProcessChainStatus(pc.id, SUCCESS) } answers {
        ctx.verify {
          assertThat(registeredPcs).doesNotContain(pc)
        }
      }
    }

    coEvery { submissionRegistry.setProcessChainEndTime(allPcs.last().id, any()) } answers {
      // on last successful process chain ...
      ctx.verify {
        assertThat(registeredPcs).doesNotContain(allPcs.last())

        // verify that all process chains were set to SUCCESS,
        // and that the results were set correctly
        coVerify(exactly = 1) {
          for (pc in allPcs) {
            submissionRegistry.setProcessChainResults(pc.id,
                mapOf("ARG1" to listOf("output-${pc.id}")))
            submissionRegistry.setProcessChainStartTime(pc.id, any())
            submissionRegistry.setProcessChainEndTime(pc.id, any())
            submissionRegistry.setProcessChainStatus(pc.id, SUCCESS)
          }
        }
      }
      ctx.completeNow()
    }

    // execute process chains
    coEvery { submissionRegistry.fetchNextProcessChain(REGISTERED, RUNNING, any()) } answers {
      if (registeredPcs.isEmpty()) null else registeredPcs.removeAt(0)
    }
    coEvery { submissionRegistry.existsProcessChain(REGISTERED, any()) } answers {
      registeredPcs.isNotEmpty() }

    vertx.eventBus().publish(AddressConstants.SCHEDULER_LOOKUP_NOW, null)
  }

  @Test
  fun oneChainOneAgent(vertx: Vertx, ctx: VertxTestContext) {
    testSimple(1, 1, vertx, ctx)
  }

  @Test
  fun twoChainsOneAgent(vertx: Vertx, ctx: VertxTestContext) {
    testSimple(2, 1, vertx, ctx)
  }

  @Test
  fun twoChainsTwoAgents(vertx: Vertx, ctx: VertxTestContext) {
    testSimple(2, 2, vertx, ctx)
  }

  @Test
  fun twentyChainsTenAgents(vertx: Vertx, ctx: VertxTestContext) {
    testSimple(20, 10, vertx, ctx)
  }

  @Test
  fun twoChainsTwoAgentsDifferentRequiredCapabilities(vertx: Vertx, ctx: VertxTestContext) {
    expectedRequiredCapabilities = listOf(listOf("docker") to 1, emptyList<String>() to 2)
    testSimple(2, 2, vertx, ctx)
  }

  @Test
  fun deallocateAgentOnError(vertx: Vertx, ctx: VertxTestContext) {
    val message = "THIS is an ERROR"

    // mock agent
    val agent = mockk<Agent>()
    val agentId = "Mock agent"
    val pc = ProcessChain()
    every { agent.id } returns agentId
    coEvery { agent.execute(any()) } throws Exception(message)
    coEvery { agentRegistry.tryAllocate(agentId, pc.id) } returns agent andThen null
    coEvery { agentRegistry.selectCandidates(any()) } returns
        listOf(Pair(emptySet(), agentId)) andThen emptyList()

    // mock submission registry
    coEvery { submissionRegistry.setProcessChainStatus(pc.id, ERROR) } just Runs
    coEvery { submissionRegistry.setProcessChainStartTime(pc.id, any()) } just Runs
    coEvery { submissionRegistry.setProcessChainEndTime(pc.id, any()) } just Runs
    coEvery { submissionRegistry.setProcessChainErrorMessage(pc.id, message) } just Runs

    coEvery { agentRegistry.deallocate(agent) } answers {
      ctx.verify {
        coVerify(exactly = 1) {
          submissionRegistry.setProcessChainStatus(pc.id, ERROR)
          submissionRegistry.setProcessChainErrorMessage(pc.id, message)
        }
      }
      ctx.completeNow()
    }

    // execute process chains
    coEvery { submissionRegistry.fetchNextProcessChain(REGISTERED, RUNNING, any()) } returns
        pc andThen null

    vertx.eventBus().publish(AddressConstants.SCHEDULER_LOOKUP_NOW, null)
  }
}
