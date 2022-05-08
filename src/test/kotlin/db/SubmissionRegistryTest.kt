package db

import assertThatThrownBy
import coVerify
import helper.JsonUtils
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.Submission
import model.processchain.Executable
import model.processchain.ProcessChain
import model.workflow.Workflow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant

/**
 * Tests for all [SubmissionRegistry] implementations
 * @author Michel Kraemer
 */
@ExtendWith(VertxExtension::class)
abstract class SubmissionRegistryTest {
  abstract fun createRegistry(vertx: Vertx): SubmissionRegistry

  private lateinit var submissionRegistry: SubmissionRegistry

  @BeforeEach
  fun setUp(vertx: Vertx) {
    submissionRegistry = createRegistry(vertx)
  }

  @AfterEach
  fun tearDownSubmissionRegistry(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.close()
      ctx.completeNow()
    }
  }

  @Test
  fun addSubmission(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      val s2 = submissionRegistry.findSubmissionById(s.id)

      ctx.verify {
        assertThat(s2).isEqualTo(s)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findSubmissionByIdNull(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      val s = submissionRegistry.findSubmissionById("DOES_NOT_EXIST")
      ctx.verify {
        assertThat(s).isNull()
      }
      ctx.completeNow()
    }
  }

  @Test
  fun findSubmissionsPage(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)
    val s2 = Submission(workflow = Workflow(), status = Submission.Status.SUCCESS)
    val s3 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addSubmission(s3)
      val js1 = JsonUtils.toJson(s1)
      val js2 = JsonUtils.toJson(s2)
      val js3 = JsonUtils.toJson(s3)

      // check if order is correct
      val r1 = submissionRegistry.findSubmissionsRaw()
      ctx.verify {
        assertThat(r1).isEqualTo(listOf(js1, js2, js3))
      }

      // check if order can be reversed
      val r2 = submissionRegistry.findSubmissionsRaw(order = -1)
      ctx.verify {
        assertThat(r2).isEqualTo(listOf(js3, js2, js1))
      }

      // check if we can query pages
      val r3 = submissionRegistry.findSubmissionsRaw(size = 1, offset = 0)
      val r4 = submissionRegistry.findSubmissionsRaw(size = 2, offset = 1)
      ctx.verify {
        assertThat(r3).isEqualTo(listOf(js1))
        assertThat(r4).isEqualTo(listOf(js2, js3))
      }

      // check if we can query pages with reversed order
      val r5 = submissionRegistry.findSubmissionsRaw(size = 1, offset = 0, order = -1)
      val r6 = submissionRegistry.findSubmissionsRaw(size = 2, offset = 1, order = -1)
      ctx.verify {
        assertThat(r5).isEqualTo(listOf(js3))
        assertThat(r6).isEqualTo(listOf(js2, js1))
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findSubmissionsByStatusPage(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)
    val s2 = Submission(workflow = Workflow(), status = Submission.Status.SUCCESS)
    val s3 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)
    val s4 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)
    val js1 = JsonUtils.toJson(s1)
    val js2 = JsonUtils.toJson(s2)
    val js3 = JsonUtils.toJson(s3)
    val js4 = JsonUtils.toJson(s4)

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addSubmission(s3)
      submissionRegistry.addSubmission(s4)

      // check if we can query by status
      val r1 = submissionRegistry.findSubmissionsRaw()
      val r2 = submissionRegistry.findSubmissionsRaw(Submission.Status.RUNNING)
      val r3 = submissionRegistry.findSubmissionsRaw(Submission.Status.SUCCESS)
      val r4 = submissionRegistry.findSubmissionsRaw(Submission.Status.ERROR)
      ctx.verify {
        assertThat(r1).isEqualTo(listOf(js1, js2, js3, js4))
        assertThat(r2).isEqualTo(listOf(js1, js3, js4))
        assertThat(r3).isEqualTo(listOf(js2))
        assertThat(r4).isEmpty()
      }

      // check if order can be reversed
      val r5 = submissionRegistry.findSubmissionsRaw(Submission.Status.RUNNING, order = -1)
      ctx.verify {
        assertThat(r5).isEqualTo(listOf(js4, js3, js1))
      }

      // check if we can query pages
      val r6 = submissionRegistry.findSubmissionsRaw(Submission.Status.RUNNING,
          size = 1, offset = 0)
      val r7 = submissionRegistry.findSubmissionsRaw(Submission.Status.RUNNING,
          size = 2, offset = 1)
      ctx.verify {
        assertThat(r6).isEqualTo(listOf(js1))
        assertThat(r7).isEqualTo(listOf(js3, js4))
      }

      // check if we can query pages with reversed order
      val r8 = submissionRegistry.findSubmissionsRaw(Submission.Status.RUNNING,
          size = 1, offset = 0, order = -1)
      val r9 = submissionRegistry.findSubmissionsRaw(Submission.Status.RUNNING,
          size = 2, offset = 1, order = -1)
      ctx.verify {
        assertThat(r8).isEqualTo(listOf(js4))
        assertThat(r9).isEqualTo(listOf(js3, js1))
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findSubmissionIdsByStatus(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)
    val s2 = Submission(workflow = Workflow(), status = Submission.Status.SUCCESS)
    val s3 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addSubmission(s3)

      val ids = submissionRegistry.findSubmissionIdsByStatus(Submission.Status.RUNNING)
      ctx.verify {
        assertThat(ids).hasSize(2)
        assertThat(ids).contains(s1.id)
        assertThat(ids).contains(s3.id)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun countSubmissions(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)
    val s2 = Submission(workflow = Workflow(), status = Submission.Status.SUCCESS)
    val s3 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)

    CoroutineScope(vertx.dispatcher()).launch {
      val c1 = submissionRegistry.countSubmissions()
      ctx.verify {
        assertThat(c1).isEqualTo(0)
      }

      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      val c2 = submissionRegistry.countSubmissions()
      ctx.verify {
        assertThat(c2).isEqualTo(2)
      }

      submissionRegistry.addSubmission(s3)
      val c3 = submissionRegistry.countSubmissions()
      ctx.verify {
        assertThat(c3).isEqualTo(3)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun countSubmissionsByStatus(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)
    val s2 = Submission(workflow = Workflow(), status = Submission.Status.SUCCESS)
    val s3 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addSubmission(s3)

      val c1 = submissionRegistry.countSubmissions()
      val c2 = submissionRegistry.countSubmissions(Submission.Status.RUNNING)
      val c3 = submissionRegistry.countSubmissions(Submission.Status.SUCCESS)
      val c4 = submissionRegistry.countSubmissions(Submission.Status.ERROR)

      ctx.verify {
        assertThat(c1).isEqualTo(3)
        assertThat(c2).isEqualTo(2)
        assertThat(c3).isEqualTo(1)
        assertThat(c4).isEqualTo(0)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun fetchNextSubmission(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      val s2 = submissionRegistry.fetchNextSubmission(Submission.Status.ACCEPTED,
          Submission.Status.RUNNING)
      val status = submissionRegistry.findSubmissionById(s.id)?.status
      val s3 = submissionRegistry.fetchNextSubmission(Submission.Status.ACCEPTED,
          Submission.Status.RUNNING)
      ctx.verify {
        assertThat(s2).isEqualTo(s)
        assertThat(s3).isNull()
        assertThat(status).isNotNull.isEqualTo(Submission.Status.RUNNING)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun fetchNextSubmissionPriority(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow(priority = -10))
    val s2 = Submission(workflow = Workflow())
    val s3 = Submission(workflow = Workflow(priority = 10))
    val s4 = Submission(workflow = Workflow())

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addSubmission(s3)
      submissionRegistry.addSubmission(s4)
      val sA = submissionRegistry.fetchNextSubmission(Submission.Status.ACCEPTED,
          Submission.Status.RUNNING)
      val sB = submissionRegistry.fetchNextSubmission(Submission.Status.ACCEPTED,
          Submission.Status.RUNNING)
      val sC = submissionRegistry.fetchNextSubmission(Submission.Status.ACCEPTED,
          Submission.Status.RUNNING)
      val sD = submissionRegistry.fetchNextSubmission(Submission.Status.ACCEPTED,
          Submission.Status.RUNNING)
      ctx.verify {
        assertThat(sA).isEqualTo(s3)
        assertThat(sB).isEqualTo(s2)
        assertThat(sC).isEqualTo(s4)
        assertThat(sD).isEqualTo(s1)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setSubmissionStartTime(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      val s1 = submissionRegistry.findSubmissionById(s.id)

      ctx.verify {
        assertThat(s1).isEqualTo(s)
      }

      val startTime = Instant.now()
      submissionRegistry.setSubmissionStartTime(s.id, startTime)
      val s2 = submissionRegistry.findSubmissionById(s.id)

      ctx.verify {
        assertThat(s2).isEqualTo(s.copy(startTime = startTime))
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setSubmissionEndTime(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      val s1 = submissionRegistry.findSubmissionById(s.id)

      ctx.verify {
        assertThat(s1).isEqualTo(s)
      }

      val endTime = Instant.now()
      submissionRegistry.setSubmissionEndTime(s.id, endTime)
      val s2 = submissionRegistry.findSubmissionById(s.id)

      ctx.verify {
        assertThat(s2).isEqualTo(s.copy(endTime = endTime))
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setSubmissionStatus(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val js = JsonUtils.toJson(s)

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      val submissions = submissionRegistry.findSubmissionsRaw()
      val acceptedSubmission1 = submissionRegistry.fetchNextSubmission(
          Submission.Status.ACCEPTED, Submission.Status.ACCEPTED)
      val runningSubmission1 = submissionRegistry.fetchNextSubmission(
          Submission.Status.RUNNING, Submission.Status.RUNNING)

      ctx.verify {
        assertThat(submissions)
            .containsExactly(js)
        assertThat(acceptedSubmission1)
            .isEqualTo(s)
        assertThat(runningSubmission1)
            .isNull()
      }

      submissionRegistry.setSubmissionStatus(s.id, Submission.Status.RUNNING)

      val acceptedSubmission2 = submissionRegistry.fetchNextSubmission(
          Submission.Status.ACCEPTED, Submission.Status.ACCEPTED)
      val runningSubmission2 = submissionRegistry.fetchNextSubmission(
          Submission.Status.RUNNING, Submission.Status.RUNNING)

      ctx.verify {
        assertThat(acceptedSubmission2)
            .isNull()
        assertThat(runningSubmission2)
            .isEqualTo(s.copy(status = Submission.Status.RUNNING))
      }

      ctx.completeNow()
    }
  }

  @Test
  fun getSubmissionStatus(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)
    val s2 = Submission(workflow = Workflow(), status = Submission.Status.SUCCESS)
    val s3 = Submission(workflow = Workflow(), status = Submission.Status.ERROR)

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addSubmission(s3)

      val status1 = submissionRegistry.getSubmissionStatus(s1.id)
      val status2 = submissionRegistry.getSubmissionStatus(s2.id)
      val status3 = submissionRegistry.getSubmissionStatus(s3.id)
      ctx.verify {
        assertThat(status1).isEqualTo(s1.status)
        assertThat(status2).isEqualTo(s2.status)
        assertThat(status3).isEqualTo(s3.status)
      }

      submissionRegistry.setSubmissionStatus(s1.id, Submission.Status.ACCEPTED)
      val status4 = submissionRegistry.getSubmissionStatus(s1.id)
      ctx.verify {
        assertThat(status4).isEqualTo(Submission.Status.ACCEPTED)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setSubmissionPriority(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow())
    val s2 = Submission(workflow = Workflow(priority = 100))

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)

      val r1 = submissionRegistry.findSubmissionById(s1.id)!!
      val r2 = submissionRegistry.findSubmissionById(s2.id)!!
      ctx.verify {
        assertThat(r1.priority).isEqualTo(0)
        assertThat(r1.workflow.priority).isEqualTo(0)
        assertThat(r2.priority).isEqualTo(100)
        assertThat(r2.workflow.priority).isEqualTo(100)
      }

      submissionRegistry.setSubmissionPriority(s1.id, -100)

      val r3 = submissionRegistry.findSubmissionById(s1.id)!!
      val r4 = submissionRegistry.findSubmissionById(s2.id)!!
      ctx.verify {
        assertThat(r3.priority).isEqualTo(-100)
        assertThat(r3.workflow.priority).isEqualTo(0) // should still be the same!
        assertThat(r4.priority).isEqualTo(100)
        assertThat(r4.workflow.priority).isEqualTo(100)
      }

      ctx.completeNow()
    }
  }

  private suspend fun doSetSubmissionResults(ctx: VertxTestContext,
      results: Map<String, List<Any>> = mapOf("ARG1" to listOf("output.txt"))): Submission {
    val s = Submission(workflow = Workflow())

    submissionRegistry.addSubmission(s)
    val results1 = submissionRegistry.getSubmissionResults(s.id)

    ctx.verify {
      assertThat(results1).isNull()
    }

    submissionRegistry.setSubmissionResults(s.id, results)
    val results2 = submissionRegistry.getSubmissionResults(s.id)

    ctx.verify {
      assertThat(results2).isEqualTo(results)
    }

    return s
  }

  @Test
  fun setSubmissionResults(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      doSetSubmissionResults(ctx)
      ctx.completeNow()
    }
  }

  @Test
  fun setSubmissionResultsNested(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      doSetSubmissionResults(ctx, mapOf("ARG1" to listOf(
          listOf("output1.txt", "output2.txt")
      )))
      ctx.completeNow()
    }
  }

  @Test
  fun resetSubmissionResults(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      val s = doSetSubmissionResults(ctx)

      submissionRegistry.setSubmissionResults(s.id, null)
      val results = submissionRegistry.getSubmissionResults(s.id)

      ctx.verify {
        assertThat(results).isNull()
      }

      ctx.completeNow()
    }
  }

  @Test
  fun getResultsOfMissingSubmission(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      ctx.coVerify {
        assertThatThrownBy {
          submissionRegistry.getSubmissionResults("MISSING")
        }.isInstanceOf(NoSuchElementException::class.java)
        ctx.completeNow()
      }
    }
  }

  private suspend fun doSetSubmissionErrorMessage(ctx: VertxTestContext): Submission {
    val s = Submission(workflow = Workflow())

    submissionRegistry.addSubmission(s)
    val errorMessage1 = submissionRegistry.getSubmissionErrorMessage(s.id)

    ctx.verify {
      assertThat(errorMessage1).isNull()
    }

    val errorMessage = "THIS is an ERROR!!!!"
    submissionRegistry.setSubmissionErrorMessage(s.id, errorMessage)
    val errorMessage2 = submissionRegistry.getSubmissionErrorMessage(s.id)

    ctx.verify {
      assertThat(errorMessage2).isEqualTo(errorMessage)
    }

    return s
  }

  @Test
  fun setSubmissionErrorMessage(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      doSetSubmissionErrorMessage(ctx)
      ctx.completeNow()
    }
  }

  @Test
  fun resetSubmissionErrorMessage(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      val s = doSetSubmissionErrorMessage(ctx)

      submissionRegistry.setSubmissionErrorMessage(s.id, null)
      val errorMessage = submissionRegistry.getSubmissionErrorMessage(s.id)

      ctx.verify {
        assertThat(errorMessage).isNull()
      }

      ctx.completeNow()
    }
  }

  @Test
  fun getErrorMessageOfMissingSubmission(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      ctx.coVerify {
        assertThatThrownBy {
          submissionRegistry.getSubmissionErrorMessage("MISSING")
        }.isInstanceOf(NoSuchElementException::class.java)
        ctx.completeNow()
      }
    }
  }

  @Test
  fun setSubmissionExecutionState(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)

      ctx.coVerify {
        assertThat(submissionRegistry.getSubmissionExecutionState(s.id)).isNull()

        val state = json {
          obj(
              "actions" to array()
          )
        }

        submissionRegistry.setSubmissionExecutionState(s.id, state)
        assertThat(submissionRegistry.getSubmissionExecutionState(s.id)).isEqualTo(state)

        submissionRegistry.setSubmissionExecutionState(s.id, null)
        assertThat(submissionRegistry.getSubmissionExecutionState(s.id)).isNull()
      }

      ctx.completeNow()
    }
  }

  @Test
  fun deleteSubmissionsFinishedBefore(vertx: Vertx, ctx: VertxTestContext) {
    val now = Instant.now()

    val s1 = Submission(workflow = Workflow())
    val pc1 = ProcessChain()
    val pc2 = ProcessChain()

    val s2 = Submission(workflow = Workflow(),
        startTime = now.minusSeconds(100))
    val pc3 = ProcessChain()
    val pc4 = ProcessChain()

    val s3 = Submission(workflow = Workflow(),
        startTime = now.minusSeconds(20),
        endTime = now.minusSeconds(10))
    val pc5 = ProcessChain()
    val pc6 = ProcessChain()

    val s4 = Submission(workflow = Workflow(),
        startTime = now.minusSeconds(200),
        endTime = now)
    val pc7 = ProcessChain()

    val s5 = Submission(workflow = Workflow(),
        startTime = now.minusSeconds(200),
        endTime = now.minusSeconds(100))
    val pc8 = ProcessChain()

    // a finished submission without an endTime
    val s6 = Submission(workflow = Workflow(),
      startTime = now.minusSeconds(200))
    val pc9 = ProcessChain()

    // an old finished submission without an endTime
    val s7 = Submission(id = "aksdfswmgo5qwwfiun4a",
      workflow = Workflow(),
      startTime = now.minusSeconds(200))
    val pc10 = ProcessChain()

    // an old finished submission without an endTime but that's still running
    val s8 = Submission(id = "aksdfswmgo5qwwfiun4b",
      workflow = Workflow(),
      startTime = now.minusSeconds(200))
    val pc11 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addSubmission(s3)
      submissionRegistry.addSubmission(s4)
      submissionRegistry.addSubmission(s5)
      submissionRegistry.addSubmission(s6)
      submissionRegistry.addSubmission(s7)
      submissionRegistry.addSubmission(s8)
      submissionRegistry.setSubmissionStatus(s1.id, Submission.Status.ACCEPTED)
      submissionRegistry.setSubmissionStatus(s2.id, Submission.Status.RUNNING)
      submissionRegistry.setSubmissionStatus(s3.id, Submission.Status.SUCCESS)
      submissionRegistry.setSubmissionStatus(s4.id, Submission.Status.SUCCESS)
      submissionRegistry.setSubmissionStatus(s5.id, Submission.Status.ERROR)
      submissionRegistry.setSubmissionStatus(s6.id, Submission.Status.ERROR)
      submissionRegistry.setSubmissionStatus(s7.id, Submission.Status.SUCCESS)
      submissionRegistry.setSubmissionStatus(s8.id, Submission.Status.RUNNING)
      submissionRegistry.addProcessChains(listOf(pc1, pc2), s1.id,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      submissionRegistry.addProcessChains(listOf(pc3, pc4), s2.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      submissionRegistry.addProcessChains(listOf(pc5, pc6), s3.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      submissionRegistry.addProcessChains(listOf(pc7), s4.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      submissionRegistry.addProcessChains(listOf(pc8), s5.id,
          SubmissionRegistry.ProcessChainStatus.ERROR)
      submissionRegistry.addProcessChains(listOf(pc9), s6.id,
        SubmissionRegistry.ProcessChainStatus.ERROR)
      submissionRegistry.addProcessChains(listOf(pc10), s7.id,
        SubmissionRegistry.ProcessChainStatus.SUCCESS)
      submissionRegistry.addProcessChains(listOf(pc11), s8.id,
        SubmissionRegistry.ProcessChainStatus.SUCCESS)

      val ids = submissionRegistry.deleteSubmissionsFinishedBefore(now.minusSeconds(5))

      ctx.coVerify {
        assertThat(ids).containsExactlyInAnyOrder(s3.id, s5.id, s7.id)

        val r1 = submissionRegistry.findProcessChains()
        assertThat(r1).containsExactlyInAnyOrder(Pair(pc1, s1.id), Pair(pc2, s1.id),
            Pair(pc3, s2.id), Pair(pc4, s2.id), Pair(pc7, s4.id), Pair(pc9, s6.id),
            Pair(pc11, s8.id))

        val r2 = submissionRegistry.findSubmissionsRaw()
        assertThat(r2.map { it.getString("id") }).containsExactlyInAnyOrder(s1.id, s2.id,
          s4.id, s6.id, s8.id)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun addProcessChain(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      submissionRegistry.addProcessChains(listOf(pc), s.id)
      val pcs = submissionRegistry.findProcessChains(s.id)
      val registeredPc = submissionRegistry.findProcessChainById(pc.id)

      ctx.verify {
        assertThat(pcs)
            .containsExactly(pc to s.id)
        assertThat(registeredPc)
            .isEqualTo(pc)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findProcessChainsPage(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)
    val pc11 = ProcessChain()
    val pc12 = ProcessChain()
    val s2 = Submission(workflow = Workflow(), status = Submission.Status.SUCCESS)
    val pc21 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addProcessChains(listOf(pc11, pc12), s1.id)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addProcessChains(listOf(pc21), s2.id)

      // check if order is correct
      val r1 = submissionRegistry.findProcessChains()
      ctx.verify {
        assertThat(r1).isEqualTo(listOf(Pair(pc11, s1.id), Pair(pc12, s1.id), Pair(pc21, s2.id)))
      }

      // check if order can be reversed
      val r2 = submissionRegistry.findProcessChains(order = -1)
      ctx.verify {
        assertThat(r2).isEqualTo(listOf(Pair(pc21, s2.id), Pair(pc12, s1.id), Pair(pc11, s1.id)))
      }

      // check if we can query pages
      val r3 = submissionRegistry.findProcessChains(size = 1, offset = 0)
      val r4 = submissionRegistry.findProcessChains(size = 2, offset = 1)
      ctx.verify {
        assertThat(r3).isEqualTo(listOf(Pair(pc11, s1.id)))
        assertThat(r4).isEqualTo(listOf(Pair(pc12, s1.id), Pair(pc21, s2.id)))
      }

      // check if we can query pages with reversed order
      val r5 = submissionRegistry.findProcessChains(size = 1, offset = 0, order = -1)
      val r6 = submissionRegistry.findProcessChains(size = 2, offset = 1, order = -1)
      ctx.verify {
        assertThat(r5).isEqualTo(listOf(Pair(pc21, s2.id)))
        assertThat(r6).isEqualTo(listOf(Pair(pc12, s1.id), Pair(pc11, s1.id)))
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findProcessChainsBySubmissionPage(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)
    val pc1 = ProcessChain()
    val pc2 = ProcessChain()
    val pc3 = ProcessChain()
    val s2 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)
    val pc4 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addProcessChains(listOf(pc1, pc2, pc3), s1.id)
      submissionRegistry.addProcessChains(listOf(pc4), s2.id)

      // check if order is correct
      val r1 = submissionRegistry.findProcessChains(s1.id)
      ctx.verify {
        assertThat(r1).isEqualTo(listOf(pc1 to s1.id, pc2 to s1.id, pc3 to s1.id))
      }

      // check if order can be reversed
      val r2 = submissionRegistry.findProcessChains(s1.id, order = -1)
      ctx.verify {
        assertThat(r2).isEqualTo(listOf(pc3 to s1.id, pc2 to s1.id, pc1 to s1.id))
      }

      // check if we can query pages
      val r3 = submissionRegistry.findProcessChains(s1.id, size = 1, offset = 0)
      val r4 = submissionRegistry.findProcessChains(s1.id, size = 2, offset = 1)
      ctx.verify {
        assertThat(r3).isEqualTo(listOf(pc1 to s1.id))
        assertThat(r4).isEqualTo(listOf(pc2 to s1.id, pc3 to s1.id))
      }

      // check if we can query pages with reversed order
      val r5 = submissionRegistry.findProcessChains(s1.id, size = 1, offset = 0, order = -1)
      val r6 = submissionRegistry.findProcessChains(s1.id, size = 2, offset = 1, order = -1)
      ctx.verify {
        assertThat(r5).isEqualTo(listOf(pc3 to s1.id))
        assertThat(r6).isEqualTo(listOf(pc2 to s1.id, pc1 to s1.id))
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findProcessChainsBySubmissionIdAndStatus(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow())
    val pc1 = ProcessChain()
    val pc2 = ProcessChain()
    val pc3 = ProcessChain()
    val s2 = Submission(workflow = Workflow())
    val pc4 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addProcessChains(listOf(pc1, pc2), s1.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      submissionRegistry.addProcessChains(listOf(pc3), s1.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      submissionRegistry.addProcessChains(listOf(pc4), s2.id)

      val statuses0 = submissionRegistry.findProcessChains(
          s1.id, SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val statuses1 = submissionRegistry.findProcessChains(
          s1.id, SubmissionRegistry.ProcessChainStatus.RUNNING)
      val statuses2 = submissionRegistry.findProcessChains(
          s1.id, SubmissionRegistry.ProcessChainStatus.SUCCESS)
      val statuses3 = submissionRegistry.findProcessChains(
          s1.id, SubmissionRegistry.ProcessChainStatus.CANCELLED)
      val statuses4 = submissionRegistry.findProcessChains(
          s2.id, SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val statuses5 = submissionRegistry.findProcessChains(
          s2.id, SubmissionRegistry.ProcessChainStatus.RUNNING)
      ctx.verify {
        assertThat(statuses0).isEmpty()
        assertThat(statuses1).isEqualTo(listOf(pc1 to s1.id, pc2 to s1.id))
        assertThat(statuses2).isEqualTo(listOf(pc3 to s1.id))
        assertThat(statuses3).isEmpty()
        assertThat(statuses4).isEqualTo(listOf(pc4 to s2.id))
        assertThat(statuses5).isEmpty()
      }

      val statuses6 = submissionRegistry.findProcessChains(
          s1.id, SubmissionRegistry.ProcessChainStatus.RUNNING, order = -1)
      ctx.verify {
        assertThat(statuses6).isEqualTo(listOf(pc2 to s1.id, pc1 to s1.id))
      }

      val statuses7 = submissionRegistry.findProcessChains(
          s1.id, SubmissionRegistry.ProcessChainStatus.RUNNING, size = 1)
      ctx.verify {
        assertThat(statuses7).isEqualTo(listOf(pc1 to s1.id))
      }

      val statuses8 = submissionRegistry.findProcessChains(
          s1.id, SubmissionRegistry.ProcessChainStatus.RUNNING, size = 1, order = -1)
      ctx.verify {
        assertThat(statuses8).isEqualTo(listOf(pc2 to s1.id))
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findProcessChainIdsByStatus(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow())
    val pc1 = ProcessChain()
    val pc2 = ProcessChain()
    val pc3 = ProcessChain()
    val s2 = Submission(workflow = Workflow())
    val pc4 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addProcessChains(listOf(pc1, pc2), s1.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      submissionRegistry.addProcessChains(listOf(pc3), s1.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      submissionRegistry.addProcessChains(listOf(pc4), s2.id)

      val statuses0 = submissionRegistry.findProcessChainIdsByStatus(
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val statuses1 = submissionRegistry.findProcessChainIdsByStatus(
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      val statuses2 = submissionRegistry.findProcessChainIdsByStatus(
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      val statuses3 = submissionRegistry.findProcessChainIdsByStatus(
          SubmissionRegistry.ProcessChainStatus.CANCELLED)
      ctx.verify {
        assertThat(statuses0).isEqualTo(listOf(pc4.id))
        assertThat(statuses1).isEqualTo(listOf(pc1.id, pc2.id))
        assertThat(statuses2).isEqualTo(listOf(pc3.id))
        assertThat(statuses3).isEmpty()
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findProcessChainIdsBySubmissionIdAndStatus(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow())
    val pc1 = ProcessChain()
    val pc2 = ProcessChain()
    val pc3 = ProcessChain()
    val s2 = Submission(workflow = Workflow())
    val pc4 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addProcessChains(listOf(pc1, pc2), s1.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      submissionRegistry.addProcessChains(listOf(pc3), s1.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      submissionRegistry.addProcessChains(listOf(pc4), s2.id)

      val statuses0 = submissionRegistry.findProcessChainIdsBySubmissionIdAndStatus(
          s1.id, SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val statuses1 = submissionRegistry.findProcessChainIdsBySubmissionIdAndStatus(
          s1.id, SubmissionRegistry.ProcessChainStatus.RUNNING)
      val statuses2 = submissionRegistry.findProcessChainIdsBySubmissionIdAndStatus(
          s1.id, SubmissionRegistry.ProcessChainStatus.SUCCESS)
      val statuses3 = submissionRegistry.findProcessChainIdsBySubmissionIdAndStatus(
          s1.id, SubmissionRegistry.ProcessChainStatus.CANCELLED)
      val statuses4 = submissionRegistry.findProcessChainIdsBySubmissionIdAndStatus(
          s2.id, SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val statuses5 = submissionRegistry.findProcessChainIdsBySubmissionIdAndStatus(
          s2.id, SubmissionRegistry.ProcessChainStatus.RUNNING)
      ctx.verify {
        assertThat(statuses0).isEmpty()
        assertThat(statuses1).isEqualTo(listOf(pc1.id, pc2.id))
        assertThat(statuses2).isEqualTo(listOf(pc3.id))
        assertThat(statuses3).isEmpty()
        assertThat(statuses4).isEqualTo(listOf(pc4.id))
        assertThat(statuses5).isEmpty()
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findProcessChainStatusesBySubmissionId(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow())
    val pc1 = ProcessChain()
    val pc2 = ProcessChain()
    val pc3 = ProcessChain()
    val s2 = Submission(workflow = Workflow())
    val pc4 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addProcessChains(listOf(pc1, pc2), s1.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      submissionRegistry.addProcessChains(listOf(pc3), s1.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      submissionRegistry.addProcessChains(listOf(pc4), s2.id)

      val statuses1 = submissionRegistry.findProcessChainStatusesBySubmissionId(s1.id)
      val statuses2 = submissionRegistry.findProcessChainStatusesBySubmissionId(s2.id)
      ctx.verify {
        assertThat(statuses1).isEqualTo(mapOf(
            pc1.id to SubmissionRegistry.ProcessChainStatus.RUNNING,
            pc2.id to SubmissionRegistry.ProcessChainStatus.RUNNING,
            pc3.id to SubmissionRegistry.ProcessChainStatus.SUCCESS
        ))
        assertThat(statuses2).isEqualTo(mapOf(
            pc4.id to SubmissionRegistry.ProcessChainStatus.REGISTERED
        ))
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findProcessChainWithoutExecutables(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow())
    val pc1 = ProcessChain(executables = listOf(Executable(path = "cp",
        serviceId = "cp", arguments = emptyList())))
    val pc2 = ProcessChain(executables = listOf(Executable(path = "sleep",
        serviceId = "sleep", arguments = emptyList())))

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addProcessChains(listOf(pc1, pc2), s1.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)

      ctx.coVerify {
        val r1 = submissionRegistry.findProcessChains()
        assertThat(r1).containsExactlyInAnyOrder(Pair(pc1, s1.id), Pair(pc2, s1.id))

        val r2 = submissionRegistry.findProcessChains(excludeExecutables = true)
        assertThat(r2).containsExactlyInAnyOrder(
            Pair(pc1.copy(executables = emptyList()), s1.id),
            Pair(pc2.copy(executables = emptyList()), s1.id)
        )
      }

      ctx.completeNow()
    }
  }

  @Test
  fun countProcessChains(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow(), status = Submission.Status.RUNNING)
    val pc11 = ProcessChain()
    val pc12 = ProcessChain()
    val s2 = Submission(workflow = Workflow(), status = Submission.Status.SUCCESS)
    val pc21 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      val r1 = submissionRegistry.countProcessChains()
      ctx.verify {
        assertThat(r1).isEqualTo(0)
      }

      submissionRegistry.addSubmission(s1)
      submissionRegistry.addProcessChains(listOf(pc11, pc12), s1.id)
      val r2 = submissionRegistry.countProcessChains()
      ctx.verify {
        assertThat(r2).isEqualTo(2)
      }

      submissionRegistry.addSubmission(s2)
      submissionRegistry.addProcessChains(listOf(pc21), s2.id)
      val r3 = submissionRegistry.countProcessChains()
      ctx.verify {
        assertThat(r3).isEqualTo(3)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun countProcessChainsBySubmissionId(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc1 = ProcessChain()
    val pc2 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      val count0 = submissionRegistry.countProcessChains(s.id)
      submissionRegistry.addProcessChains(listOf(pc1, pc2), s.id)
      val count2 = submissionRegistry.countProcessChains(s.id)

      ctx.verify {
        assertThat(count0).isEqualTo(0)
        assertThat(count2).isEqualTo(2)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun countProcessChainsByStatus(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc1 = ProcessChain()
    val pc2 = ProcessChain()
    val pc3 = ProcessChain()
    val pc4 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      val count0 = submissionRegistry.countProcessChains(s.id,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      submissionRegistry.addProcessChains(listOf(pc1), s.id)
      val count1 = submissionRegistry.countProcessChains(s.id,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      submissionRegistry.addProcessChains(listOf(pc2, pc3), s.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      val count2 = submissionRegistry.countProcessChains(s.id,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val count3 = submissionRegistry.countProcessChains(s.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      submissionRegistry.addProcessChains(listOf(pc4), s.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      val count4 = submissionRegistry.countProcessChains(s.id,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val count5 = submissionRegistry.countProcessChains(s.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      val count6 = submissionRegistry.countProcessChains(s.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)

      ctx.verify {
        assertThat(count0).isEqualTo(0)
        assertThat(count1).isEqualTo(1)
        assertThat(count2).isEqualTo(1)
        assertThat(count3).isEqualTo(2)
        assertThat(count4).isEqualTo(1)
        assertThat(count5).isEqualTo(2)
        assertThat(count6).isEqualTo(1)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun countProcessChainsBySubmissionIdAndRequiredCapabilitySet(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc1 = ProcessChain(requiredCapabilities = setOf("docker", "gpu"))
    val pc2 = ProcessChain(requiredCapabilities = setOf("docker", "gpu"))
    val pc3 = ProcessChain(requiredCapabilities = setOf("docker"))
    val pc4 = ProcessChain(requiredCapabilities = setOf("foobar"))
    val pc5 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      val count0 = submissionRegistry.countProcessChains(s.id,
          requiredCapabilities = setOf("docker", "gpu"))
      submissionRegistry.addProcessChains(listOf(pc1), s.id)
      val count1 = submissionRegistry.countProcessChains(s.id,
          requiredCapabilities = setOf("docker", "gpu"))
      submissionRegistry.addProcessChains(listOf(pc2, pc3, pc4, pc5), s.id)
      val count2 = submissionRegistry.countProcessChains(s.id,
          requiredCapabilities = setOf("docker", "gpu"))
      val count3 = submissionRegistry.countProcessChains(s.id,
          requiredCapabilities = setOf("docker"))
      val count4 = submissionRegistry.countProcessChains(s.id,
          requiredCapabilities = setOf("foobar"))
      val count5 = submissionRegistry.countProcessChains(s.id,
          requiredCapabilities = setOf("missing"))

      ctx.verify {
        assertThat(count0).isEqualTo(0)
        assertThat(count1).isEqualTo(1)
        assertThat(count2).isEqualTo(2)
        assertThat(count3).isEqualTo(1)
        assertThat(count4).isEqualTo(1)
        assertThat(count5).isEqualTo(0)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun countProcessChainsPerStatus(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow())
    val pc1 = ProcessChain()
    val pc2 = ProcessChain()
    val pc3 = ProcessChain()
    val pc4 = ProcessChain()
    val s2 = Submission(workflow = Workflow())
    val pc5 = ProcessChain()
    val pc6 = ProcessChain()
    val pc7 = ProcessChain()
    val pc8 = ProcessChain()
    val pc9 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addProcessChains(listOf(pc1, pc2, pc3), s1.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      submissionRegistry.addProcessChains(listOf(pc4), s1.id,
          SubmissionRegistry.ProcessChainStatus.ERROR)

      submissionRegistry.addSubmission(s2)
      submissionRegistry.addProcessChains(listOf(pc5), s2.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      submissionRegistry.addProcessChains(listOf(pc6, pc7), s2.id,
          SubmissionRegistry.ProcessChainStatus.ERROR)
      submissionRegistry.addProcessChains(listOf(pc8), s2.id,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      submissionRegistry.addProcessChains(listOf(pc9), s2.id,
          SubmissionRegistry.ProcessChainStatus.CANCELLED)

      ctx.coVerify {
        assertThat(submissionRegistry.countProcessChainsPerStatus())
            .isEqualTo(mapOf(
                SubmissionRegistry.ProcessChainStatus.SUCCESS to 3L,
                SubmissionRegistry.ProcessChainStatus.ERROR to 3L,
                SubmissionRegistry.ProcessChainStatus.RUNNING to 1L,
                SubmissionRegistry.ProcessChainStatus.REGISTERED to 1L,
                SubmissionRegistry.ProcessChainStatus.CANCELLED to 1L
            ))

        assertThat(submissionRegistry.countProcessChainsPerStatus(s1.id))
            .isEqualTo(mapOf(
                SubmissionRegistry.ProcessChainStatus.SUCCESS to 3L,
                SubmissionRegistry.ProcessChainStatus.ERROR to 1L
            ))

        assertThat(submissionRegistry.countProcessChainsPerStatus(s2.id))
            .isEqualTo(mapOf(
                SubmissionRegistry.ProcessChainStatus.RUNNING to 1L,
                SubmissionRegistry.ProcessChainStatus.ERROR to 2L,
                SubmissionRegistry.ProcessChainStatus.REGISTERED to 1L,
                SubmissionRegistry.ProcessChainStatus.CANCELLED to 1L
            ))
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findProcessChainRequiredCapabilities(vertx: Vertx, ctx: VertxTestContext) {
    val rcs1 = setOf("rc1")
    val rcs2 = setOf("rc1", "rc2")

    val s = Submission(workflow = Workflow())
    val pc1 = ProcessChain(requiredCapabilities = rcs1)
    val pc2 = ProcessChain()
    val pc3 = ProcessChain(requiredCapabilities = rcs1)
    val pc4 = ProcessChain(requiredCapabilities = rcs2)
    val pc5 = ProcessChain(requiredCapabilities = rcs2)
    val pc6 = ProcessChain(requiredCapabilities = rcs1)

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      submissionRegistry.addProcessChains(listOf(pc1, pc2, pc3, pc4, pc5, pc6), s.id)

      val r = submissionRegistry.findProcessChainRequiredCapabilities(
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      ctx.verify {
        assertThat(r).hasSize(3)
        assertThat(r).anyMatch { it.toSet() == rcs1 }
        assertThat(r).anyMatch { it.toSet() == rcs2 }
        assertThat(r).anyMatch { it.isEmpty() }
      }

      ctx.completeNow()
    }
  }

  @Test
  fun fetchNextProcessChain(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      submissionRegistry.addProcessChains(listOf(pc), s.id)
      val pc2 = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      val status = submissionRegistry.getProcessChainStatus(pc.id)
      val pc3 = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      ctx.verify {
        assertThat(pc2).isEqualTo(pc)
        assertThat(pc3).isNull()
        assertThat(status).isEqualTo(SubmissionRegistry.ProcessChainStatus.RUNNING)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun fetchNextProcessChainOrder(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc1 = ProcessChain()
    val pc2 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      submissionRegistry.addProcessChains(listOf(pc1, pc2), s.id)
      val pcA = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      val pcB = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      ctx.verify {
        assertThat(pcA).isEqualTo(pc1)
        assertThat(pcB).isEqualTo(pc2)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun fetchNextProcessChainPriority(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc1 = ProcessChain(priority = -10)
    val pc2 = ProcessChain()
    val pc3 = ProcessChain(priority = 10)
    val pc4 = ProcessChain(priority = 0)
    val pc5 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      submissionRegistry.addProcessChains(listOf(pc1, pc2, pc3, pc4, pc5), s.id)
      val pcA = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      val pcB = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      val pcC = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      val pcD = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      val pcE = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      ctx.verify {
        assertThat(pcA).isEqualTo(pc3)
        assertThat(pcB).isEqualTo(pc2)
        assertThat(pcC).isEqualTo(pc4)
        assertThat(pcD).isEqualTo(pc5)
        assertThat(pcE).isEqualTo(pc1)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun fetchNextProcessChainRequiredCapabilities(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc1 = ProcessChain(requiredCapabilities = setOf("docker", "sleep"))
    val pc2 = ProcessChain()
    val pc3 = ProcessChain(requiredCapabilities = setOf("docker"))

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      submissionRegistry.addProcessChains(listOf(pc1, pc2, pc3), s.id)
      val pcA = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val pcB = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          listOf("docker"))
      val pcC = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          listOf("docker", "sleep"))
      val pcD = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          emptyList())
      ctx.verify {
        assertThat(pcA).isEqualTo(pc1)
        assertThat(pcB).isEqualTo(pc3)
        assertThat(pcC).isEqualTo(pc1)
        assertThat(pcD).isEqualTo(pc2)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun existsProcessChain(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc1 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      val eA = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      submissionRegistry.addProcessChains(listOf(pc1), s.id)
      val eB = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      submissionRegistry.setProcessChainStatus(pc1.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      val eC = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val eD = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.SUCCESS)

      ctx.verify {
        assertThat(eA).isFalse
        assertThat(eB).isTrue
        assertThat(eC).isFalse
        assertThat(eD).isTrue()
      }

      ctx.completeNow()
    }
  }

  @Test
  fun existsProcessChainCapabilities(vertx: Vertx, ctx: VertxTestContext) {
    val rc = setOf("docker")
    val s = Submission(workflow = Workflow())
    val pc1 = ProcessChain(requiredCapabilities = rc)
    val pc2 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      val eA1 = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val eA2 = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED, rc)

      submissionRegistry.addProcessChains(listOf(pc1, pc2), s.id)
      val eB1 = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val eB2 = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED, rc)

      submissionRegistry.setProcessChainStatus(pc1.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      val eC1 = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val eC2 = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED, rc)

      val eD1 = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      val eD2 = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.SUCCESS, rc)

      submissionRegistry.setProcessChainStatus(pc2.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      val eE1 = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val eE2 = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED, rc)

      val eF1 = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      val eF2 = submissionRegistry.existsProcessChain(
          SubmissionRegistry.ProcessChainStatus.SUCCESS, rc)

      ctx.verify {
        assertThat(eA1).isFalse
        assertThat(eA2).isFalse
        assertThat(eB1).isTrue
        assertThat(eB2).isTrue
        assertThat(eC1).isTrue
        assertThat(eC2).isFalse
        assertThat(eD1).isTrue
        assertThat(eD2).isTrue
        assertThat(eE1).isFalse
        assertThat(eE2).isFalse
        assertThat(eF1).isTrue
        assertThat(eF2).isTrue()
      }

      ctx.completeNow()
    }
  }

  @Test
  fun getProcessChainSubmissionId(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      submissionRegistry.addProcessChains(listOf(pc), s.id)
      val sid = submissionRegistry.getProcessChainSubmissionId(pc.id)
      ctx.verify {
        assertThat(sid).isEqualTo(s.id)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun getSubmissionIdOfMissingProcessChain(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      ctx.coVerify {
        assertThatThrownBy {
          submissionRegistry.getProcessChainSubmissionId("MISSING")
        }.isInstanceOf(NoSuchElementException::class.java)
      }
      ctx.completeNow()
    }
  }

  @Test
  fun addProcessChainToMissingSubmission(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      ctx.coVerify {
        assertThatThrownBy {
          submissionRegistry.addProcessChains(listOf(ProcessChain()), "MISSING")
        }.isInstanceOf(NoSuchElementException::class.java)
        ctx.completeNow()
      }
    }
  }

  @Test
  fun setProcessChainStartTime(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      submissionRegistry.addProcessChains(listOf(pc), s.id)
      val startTime1 = submissionRegistry.getProcessChainStartTime(pc.id)

      ctx.verify {
        assertThat(startTime1).isNull()
      }

      val newStartTime = Instant.now()
      submissionRegistry.setProcessChainStartTime(pc.id, newStartTime)
      val startTime2 = submissionRegistry.getProcessChainStartTime(pc.id)

      ctx.verify {
        assertThat(startTime2).isEqualTo(newStartTime)
      }

      submissionRegistry.setProcessChainStartTime(pc.id, null)
      val startTime3 = submissionRegistry.getProcessChainStartTime(pc.id)

      ctx.verify {
        assertThat(startTime3).isNull()
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setProcessChainEndTime(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      submissionRegistry.addProcessChains(listOf(pc), s.id)
      val endTime1 = submissionRegistry.getProcessChainEndTime(pc.id)

      ctx.verify {
        assertThat(endTime1).isNull()
      }

      val newEndTime = Instant.now()
      submissionRegistry.setProcessChainEndTime(pc.id, newEndTime)
      val endTime2 = submissionRegistry.getProcessChainEndTime(pc.id)

      ctx.verify {
        assertThat(endTime2).isEqualTo(newEndTime)
      }

      submissionRegistry.setProcessChainEndTime(pc.id, null)
      val endTime3 = submissionRegistry.getProcessChainEndTime(pc.id)

      ctx.verify {
        assertThat(endTime3).isNull()
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setProcessChainStatus(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      submissionRegistry.addProcessChains(listOf(pc), s.id)
      val pcs = submissionRegistry.findProcessChains(s.id)
      val registeredPc1 = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val runningPc1 = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.RUNNING,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      val pcStatus1 = submissionRegistry.getProcessChainStatus(pc.id)

      ctx.verify {
        assertThat(pcs)
            .containsExactly(pc to s.id)
        assertThat(registeredPc1)
            .isEqualTo(pc)
        assertThat(runningPc1)
            .isNull()
        assertThat(pcStatus1)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.REGISTERED)
      }

      submissionRegistry.setProcessChainStatus(pc.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)

      val registeredPc2 = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      val runningPc2 = submissionRegistry.fetchNextProcessChain(
          SubmissionRegistry.ProcessChainStatus.RUNNING,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      val pcStatus2 = submissionRegistry.getProcessChainStatus(pc.id)

      ctx.verify {
        assertThat(registeredPc2)
            .isNull()
        assertThat(runningPc2)
            .isEqualTo(pc)
        assertThat(pcStatus2)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.RUNNING)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setProcessChainStatusExpected(vertx: Vertx, ctx: VertxTestContext) {
    val s = Submission(workflow = Workflow())
    val pc = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s)
      submissionRegistry.addProcessChains(listOf(pc), s.id)

      val pcStatus1 = submissionRegistry.getProcessChainStatus(pc.id)
      ctx.verify {
        assertThat(pcStatus1)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.REGISTERED)
      }

      submissionRegistry.setProcessChainStatus(pc.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)

      val pcStatus2 = submissionRegistry.getProcessChainStatus(pc.id)
      ctx.verify {
        assertThat(pcStatus2)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.RUNNING)
      }

      submissionRegistry.setProcessChainStatus(pc.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING,
          SubmissionRegistry.ProcessChainStatus.CANCELLED)

      val pcStatus3 = submissionRegistry.getProcessChainStatus(pc.id)
      ctx.verify {
        assertThat(pcStatus3)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.CANCELLED)
      }

      submissionRegistry.setProcessChainStatus(pc.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)

      val pcStatus4 = submissionRegistry.getProcessChainStatus(pc.id)
      ctx.verify {
        assertThat(pcStatus4)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.CANCELLED)
      }

      submissionRegistry.setProcessChainStatus(pc.id,
          SubmissionRegistry.ProcessChainStatus.CANCELLED,
          SubmissionRegistry.ProcessChainStatus.ERROR)

      val pcStatus5 = submissionRegistry.getProcessChainStatus(pc.id)
      ctx.verify {
        assertThat(pcStatus5)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.ERROR)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setAllProcessChainsStatus(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow())
    val pc1 = ProcessChain()
    val pc2 = ProcessChain()
    val pc3 = ProcessChain()

    val s2 = Submission(workflow = Workflow())
    val pc4 = ProcessChain()
    val pc5 = ProcessChain()

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addProcessChains(listOf(pc1), s1.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      submissionRegistry.addProcessChains(listOf(pc2, pc3), s1.id,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)

      submissionRegistry.addSubmission(s2)
      submissionRegistry.addProcessChains(listOf(pc4, pc5), s2.id,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)

      val pcStatus1a = submissionRegistry.getProcessChainStatus(pc1.id)
      val pcStatus2a = submissionRegistry.getProcessChainStatus(pc2.id)
      val pcStatus3a = submissionRegistry.getProcessChainStatus(pc3.id)
      val pcStatus4a = submissionRegistry.getProcessChainStatus(pc4.id)
      val pcStatus5a = submissionRegistry.getProcessChainStatus(pc5.id)

      ctx.verify {
        assertThat(pcStatus1a)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.RUNNING)
        assertThat(pcStatus2a)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.REGISTERED)
        assertThat(pcStatus3a)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.REGISTERED)
        assertThat(pcStatus4a)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.REGISTERED)
        assertThat(pcStatus5a)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.REGISTERED)
      }

      submissionRegistry.setAllProcessChainsStatus(s1.id,
          SubmissionRegistry.ProcessChainStatus.REGISTERED,
          SubmissionRegistry.ProcessChainStatus.CANCELLED)

      val pcStatus1b = submissionRegistry.getProcessChainStatus(pc1.id)
      val pcStatus2b = submissionRegistry.getProcessChainStatus(pc2.id)
      val pcStatus3b = submissionRegistry.getProcessChainStatus(pc3.id)
      val pcStatus4b = submissionRegistry.getProcessChainStatus(pc4.id)
      val pcStatus5b = submissionRegistry.getProcessChainStatus(pc5.id)

      ctx.verify {
        assertThat(pcStatus1b)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.RUNNING)
        assertThat(pcStatus2b)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.CANCELLED)
        assertThat(pcStatus3b)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.CANCELLED)
        assertThat(pcStatus4b)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.REGISTERED)
        assertThat(pcStatus5b)
            .isEqualTo(SubmissionRegistry.ProcessChainStatus.REGISTERED)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun getStatusOfMissingProcessChain(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      ctx.coVerify {
        assertThatThrownBy {
          submissionRegistry.getProcessChainStatus("MISSING")
        }.isInstanceOf(NoSuchElementException::class.java)
        ctx.completeNow()
      }
    }
  }

  @Test
  fun setProcessChainPriority(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      val s = Submission(workflow = Workflow())
      val pc = ProcessChain()

      submissionRegistry.addSubmission(s)
      submissionRegistry.addProcessChains(listOf(pc), s.id)
      val r1 = submissionRegistry.findProcessChainById(pc.id)!!

      ctx.verify {
        assertThat(r1.priority).isEqualTo(0)
      }

      submissionRegistry.setProcessChainPriority(pc.id, 10)
      val r2 = submissionRegistry.findProcessChainById(pc.id)!!

      ctx.verify {
        assertThat(r2.priority).isEqualTo(10)
      }

      submissionRegistry.setProcessChainPriority(pc.id, -10)
      val r3 = submissionRegistry.findProcessChainById(pc.id)!!

      ctx.verify {
        assertThat(r3.priority).isEqualTo(-10)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setAllProcessChainPriority(vertx: Vertx, ctx: VertxTestContext) {
    val s1 = Submission(workflow = Workflow())
    val pc1 = ProcessChain(priority = -10)
    val pc2 = ProcessChain()
    val pc3 = ProcessChain()

    val s2 = Submission(workflow = Workflow())
    val pc4 = ProcessChain()
    val pc5 = ProcessChain(priority = 100)

    CoroutineScope(vertx.dispatcher()).launch {
      submissionRegistry.addSubmission(s1)
      submissionRegistry.addProcessChains(listOf(pc1), s1.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      submissionRegistry.addProcessChains(listOf(pc2, pc3), s1.id,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)

      submissionRegistry.addSubmission(s2)
      submissionRegistry.addProcessChains(listOf(pc4, pc5), s2.id,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)

      val r11 = submissionRegistry.findProcessChains(submissionId = s1.id).map { it.first }
      val r12 = submissionRegistry.findProcessChains(submissionId = s2.id).map { it.first }
      ctx.verify {
        assertThat(r11).hasSize(3)
        assertThat(r11[0]).isEqualTo(pc1)
        assertThat(r11[1]).isEqualTo(pc2)
        assertThat(r11[2]).isEqualTo(pc3)

        assertThat(r12).hasSize(2)
        assertThat(r12[0]).isEqualTo(pc4)
        assertThat(r12[1]).isEqualTo(pc5)
      }

      submissionRegistry.setAllProcessChainsPriority(s1.id, -1000)

      val r21 = submissionRegistry.findProcessChains(submissionId = s1.id).map { it.first }
      val r22 = submissionRegistry.findProcessChains(submissionId = s2.id).map { it.first }
      ctx.verify {
        assertThat(r21).hasSize(3)
        assertThat(r21[0].priority).isEqualTo(-1000)
        assertThat(r21[1].priority).isEqualTo(-1000)
        assertThat(r21[2].priority).isEqualTo(-1000)

        assertThat(r22).hasSize(2)
        assertThat(r22[0]).isEqualTo(pc4)
        assertThat(r22[1]).isEqualTo(pc5)
      }

      ctx.completeNow()
    }
  }

  private suspend fun doSetProcessChainResults(ctx: VertxTestContext,
      results: Map<String, List<Any>> = mapOf("ARG1" to listOf("output.txt"))): ProcessChain {
    val s = Submission(workflow = Workflow())
    val pc = ProcessChain()

    submissionRegistry.addSubmission(s)
    submissionRegistry.addProcessChains(listOf(pc), s.id)
    val pcResults1 = submissionRegistry.getProcessChainResults(pc.id)

    ctx.verify {
      assertThat(pcResults1).isNull()
    }

    submissionRegistry.setProcessChainResults(pc.id, results)
    val pcResults2 = submissionRegistry.getProcessChainResults(pc.id)

    ctx.verify {
      assertThat(pcResults2).isEqualTo(results)
    }

    return pc
  }

  @Test
  fun setProcessChainResults(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      doSetProcessChainResults(ctx)
      ctx.completeNow()
    }
  }

  @Test
  fun setProcessChainResultsNested(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      doSetProcessChainResults(ctx, mapOf("ARG1" to listOf(
          listOf("output1.txt", "output2.txt")
      )))
      ctx.completeNow()
    }
  }

  @Test
  fun resetProcessChainResults(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      val pc = doSetProcessChainResults(ctx)

      submissionRegistry.setProcessChainResults(pc.id, null)
      val pcResults = submissionRegistry.getProcessChainResults(pc.id)

      ctx.verify {
        assertThat(pcResults).isNull()
      }

      ctx.completeNow()
    }
  }

  @Test
  fun getResultsOfMissingProcessChain(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      ctx.coVerify {
        assertThatThrownBy {
          submissionRegistry.getProcessChainResults("MISSING")
        }.isInstanceOf(NoSuchElementException::class.java)
        ctx.completeNow()
      }
    }
  }

  @Test
  fun getProcessChainStatusAndResultsIfFinished(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      val s1 = Submission(workflow = Workflow())
      val s2 = Submission(workflow = Workflow())
      val pc1 = ProcessChain()
      val pc2 = ProcessChain()
      val pc3 = ProcessChain()
      val pc4 = ProcessChain()
      val pc5 = ProcessChain()
      val pc6 = ProcessChain()
      val pc7 = ProcessChain()

      submissionRegistry.addSubmission(s1)
      submissionRegistry.addSubmission(s2)
      submissionRegistry.addProcessChains(listOf(pc1), s1.id,
          SubmissionRegistry.ProcessChainStatus.REGISTERED)
      submissionRegistry.addProcessChains(listOf(pc2), s1.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)
      submissionRegistry.addProcessChains(listOf(pc3), s1.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      submissionRegistry.addProcessChains(listOf(pc4), s1.id,
          SubmissionRegistry.ProcessChainStatus.CANCELLED)
      submissionRegistry.addProcessChains(listOf(pc5), s1.id,
          SubmissionRegistry.ProcessChainStatus.ERROR)
      submissionRegistry.addProcessChains(listOf(pc6), s2.id,
          SubmissionRegistry.ProcessChainStatus.SUCCESS)
      submissionRegistry.addProcessChains(listOf(pc7), s2.id,
          SubmissionRegistry.ProcessChainStatus.RUNNING)

      val results1 = mapOf("output" to listOf("/tmp/out/file1"))
      val results2 = mapOf("output" to listOf("/tmp/out/file2"))
      submissionRegistry.setProcessChainResults(pc3.id, results1)
      submissionRegistry.setProcessChainResults(pc6.id, results2)

      ctx.coVerify {
        val r1 = submissionRegistry.getProcessChainStatusAndResultsIfFinished(emptyList())
        assertThat(r1).isEmpty()

        val r2 = submissionRegistry.getProcessChainStatusAndResultsIfFinished(listOf(pc1.id))
        assertThat(r2).isEmpty()

        val r3 = submissionRegistry.getProcessChainStatusAndResultsIfFinished(listOf(pc3.id))
        assertThat(r3).isEqualTo(mapOf(pc3.id to (SubmissionRegistry.ProcessChainStatus.SUCCESS to results1)))

        val r4 = submissionRegistry.getProcessChainStatusAndResultsIfFinished(
            listOf(pc3.id, pc4.id))
        assertThat(r4).isEqualTo(mapOf(
            pc3.id to (SubmissionRegistry.ProcessChainStatus.SUCCESS to results1),
            pc4.id to (SubmissionRegistry.ProcessChainStatus.CANCELLED to null)
        ))

        // also check what happens if the process chain IDs are provided as a set
        val r5 = submissionRegistry.getProcessChainStatusAndResultsIfFinished(
            setOf(pc3.id, pc4.id))
        assertThat(r5).isEqualTo(mapOf(
            pc3.id to (SubmissionRegistry.ProcessChainStatus.SUCCESS to results1),
            pc4.id to (SubmissionRegistry.ProcessChainStatus.CANCELLED to null)
        ))

        val r6 = submissionRegistry.getProcessChainStatusAndResultsIfFinished(
            listOf(pc1.id, pc2.id, pc3.id, pc4.id, pc5.id, pc6.id, pc7.id))
        assertThat(r6).isEqualTo(mapOf(
            pc3.id to (SubmissionRegistry.ProcessChainStatus.SUCCESS to results1),
            pc4.id to (SubmissionRegistry.ProcessChainStatus.CANCELLED to null),
            pc5.id to (SubmissionRegistry.ProcessChainStatus.ERROR to null),
            pc6.id to (SubmissionRegistry.ProcessChainStatus.SUCCESS to results2)
        ))
      }

      ctx.completeNow()
    }
  }

  private suspend fun doSetProcessChainErrorMessage(ctx: VertxTestContext): ProcessChain {
    val s = Submission(workflow = Workflow())
    val pc = ProcessChain()

    submissionRegistry.addSubmission(s)
    submissionRegistry.addProcessChains(listOf(pc), s.id)
    val pcErrorMessage1 = submissionRegistry.getProcessChainErrorMessage(pc.id)

    ctx.verify {
      assertThat(pcErrorMessage1).isNull()
    }

    val errorMessage = "THIS is an ERROR!!!!"
    submissionRegistry.setProcessChainErrorMessage(pc.id, errorMessage)
    val pcErrorMessage2 = submissionRegistry.getProcessChainErrorMessage(pc.id)

    ctx.verify {
      assertThat(pcErrorMessage2).isEqualTo(errorMessage)
    }

    return pc
  }

  @Test
  fun setProcessChainErrorMessage(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      doSetProcessChainErrorMessage(ctx)
      ctx.completeNow()
    }
  }

  @Test
  fun resetProcessChainErrorMessage(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      val pc = doSetProcessChainErrorMessage(ctx)

      submissionRegistry.setProcessChainErrorMessage(pc.id, null)
      val pcErrorMessage = submissionRegistry.getProcessChainErrorMessage(pc.id)

      ctx.verify {
        assertThat(pcErrorMessage).isNull()
      }

      ctx.completeNow()
    }
  }

  @Test
  fun getErrorMessageOfMissingProcessChain(vertx: Vertx, ctx: VertxTestContext) {
    CoroutineScope(vertx.dispatcher()).launch {
      ctx.coVerify {
        assertThatThrownBy {
          submissionRegistry.getProcessChainErrorMessage("MISSING")
        }.isInstanceOf(NoSuchElementException::class.java)
        ctx.completeNow()
      }
    }
  }
}
