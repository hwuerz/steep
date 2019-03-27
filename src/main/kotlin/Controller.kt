import AddressConstants.CONTROLLER_LOOKUP_NOW
import AddressConstants.CONTROLLER_LOOKUP_ORPHANS_NOW
import ConfigConstants.CONTROLLER_LOOKUP_INTERVAL
import ConfigConstants.CONTROLLER_LOOKUP_ORPHANS_INTERVAL
import ConfigConstants.TMP_PATH
import db.MetadataRegistry
import db.MetadataRegistryFactory
import db.SubmissionRegistry
import db.SubmissionRegistry.ProcessChainStatus
import db.SubmissionRegistryFactory
import io.vertx.core.shareddata.Lock
import io.vertx.kotlin.core.shareddata.getLockWithTimeoutAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.Submission
import model.processchain.ProcessChain
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * The controller fetches submissions from a [SubmissionRegistry], converts
 * them to process chains, sends these chains to the [Scheduler] to execute
 * them, and finally and puts the results back into the [SubmissionRegistry].
 * @author Michel Kraemer
 */
class Controller : CoroutineVerticle() {
  companion object {
    private val log = LoggerFactory.getLogger(Controller::class.java)
    private const val DEFAULT_LOOKUP_INTERVAL = 2000L
    private const val DEFAULT_LOOKUP_ORPHANS_INTERVAL = 300_000L
    private const val PROCESSING_SUBMISSION_LOCK_PREFIX = "Controller.ProcessingSubmission."
  }

  private lateinit var tmpPath: String
  private lateinit var submissionRegistry: SubmissionRegistry
  private lateinit var metadataRegistry: MetadataRegistry

  private var lookupInterval: Long = DEFAULT_LOOKUP_INTERVAL
  private lateinit var periodicLookupJob: Job
  private var lookupOrphansInterval: Long = DEFAULT_LOOKUP_ORPHANS_INTERVAL
  private lateinit var periodicLookupOrphansJob: Job

  override suspend fun start() {
    log.info("Launching controller ...")

    // create registries
    submissionRegistry = SubmissionRegistryFactory.create(vertx)
    metadataRegistry = MetadataRegistryFactory.create(vertx)

    // read configuration
    tmpPath = config.getString(TMP_PATH) ?: throw IllegalStateException(
        "Missing configuration item `$TMP_PATH'")
    lookupInterval = config.getLong(CONTROLLER_LOOKUP_INTERVAL, lookupInterval)
    lookupOrphansInterval = config.getLong(CONTROLLER_LOOKUP_ORPHANS_INTERVAL,
        lookupOrphansInterval)

    // periodically look for new submissions and execute them
    periodicLookupJob = launch {
      while (true) {
        delay(lookupInterval)
        lookup()
      }
    }

    // periodically look for orphaned running submissions and re-execute them
    periodicLookupOrphansJob = launch {
      while (true) {
        delay(lookupOrphansInterval)
        lookupOrphans()
      }
    }
    launch {
      // look up for orphaned running submissions now
      lookupOrphans()
    }

    vertx.eventBus().consumer<Unit>(CONTROLLER_LOOKUP_NOW) {
      launch {
        lookup()
      }
    }

    vertx.eventBus().consumer<Unit>(CONTROLLER_LOOKUP_ORPHANS_NOW) {
      launch {
        lookupOrphans()
      }
    }
  }

  override suspend fun stop() {
    log.info("Stopping controller ...")
    periodicLookupJob.cancelAndJoin()
    periodicLookupOrphansJob.cancelAndJoin()
  }

  /**
   * Get new submissions and execute them asynchronously
   */
  private suspend fun lookup() {
    while (true) {
      // get next submission
      val submission = submissionRegistry.fetchNextSubmission(
          Submission.Status.ACCEPTED, Submission.Status.RUNNING) ?: return
      log.info("Found new submission `${submission.id}'")

      // execute submission asynchronously
      launch {
        runSubmission(submission)
      }
    }
  }

  /**
   * Check for orphaned running submissions and resume their execution
   */
  private suspend fun lookupOrphans() {
    val ids = submissionRegistry.findSubmissionIdsByStatus(Submission.Status.RUNNING)
    for (id in ids) {
      val lock = tryLockSubmission(id)
      if (lock != null) {
        lock.release()
        val s = submissionRegistry.findSubmissionById(id)
        if (s != null) {
          log.info("Found orphaned running submission `$id'. Trying to resume ...")
          launch {
            runSubmission(s)
          }
        }
      }
    }
  }

  /**
   * Tries to create a lock for the submission with the given [submissionId].
   * As long as the lock is held, the submission is being processed. The method
   * returns `null` if the lock could not be acquired. The reason for this is
   * most likely that the submission is already being processed.
   */
  private suspend fun tryLockSubmission(submissionId: String): Lock? {
    val lockName = PROCESSING_SUBMISSION_LOCK_PREFIX + submissionId
    return try {
      vertx.sharedData().getLockWithTimeoutAwait(lockName, 1)
    } catch (t: Throwable) {
      // Could not acquire lock. Assume someone else is already processing
      // this submission
      null
    }
  }

  /**
   * Execute a submission
   * @param submission the submission to execute
   */
  private suspend fun runSubmission(submission: Submission) {
    val lock = tryLockSubmission(submission.id)
    if (lock == null) {
      log.debug("The submission `${submission.id}' is already being executed")
      return
    }

    try {
      // check twice - the submission may have already been processed before we
      // were able to acquire the lock
      val actualStatus = submissionRegistry.getSubmissionStatus(submission.id)
      if (actualStatus != Submission.Status.RUNNING) {
        log.debug("Expected submission to be in status `${Submission.Status.RUNNING}' " +
            "but it was in status `$actualStatus'. Skipping execution.")
        return
      }

      val ruleSystem = RuleSystem(submission.workflow,
          FilenameUtils.normalize("$tmpPath/${submission.id}"),
          metadataRegistry.findServices())

      // check if submission needs to be resumed
      var processChainsToResume: Collection<ProcessChain>? = null
      val executionState = submissionRegistry.getSubmissionExecutionState(submission.id)
      if (executionState == null) {
        // submission has not been started before - start it now
        submissionRegistry.setSubmissionStartTime(submission.id, Instant.now())
      } else {
        log.info("Resuming submission `${submission.id}' ...")

        // resume aborted submissions...
        // load rule system state
        ruleSystem.loadState(executionState)

        // reset running process chains and repeat failed process chains
        val runningProcessChains = submissionRegistry.countProcessChainsByStatus(submission.id,
            ProcessChainStatus.RUNNING)
        val failedProcessChains = submissionRegistry.countProcessChainsByStatus(submission.id,
            ProcessChainStatus.ERROR)
        if (runningProcessChains > 0 || failedProcessChains > 0) {
          val pcstatuses = submissionRegistry.findProcessChainStatusesBySubmissionId(submission.id)
          for ((pcId, pcstatus) in pcstatuses) {
            if (pcstatus === ProcessChainStatus.RUNNING || pcstatus === ProcessChainStatus.ERROR) {
              submissionRegistry.setProcessChainStatus(pcId, ProcessChainStatus.REGISTERED)
            }
            if (pcstatus === ProcessChainStatus.ERROR) {
              submissionRegistry.setProcessChainErrorMessage(pcId, null)
            }
          }
        }

        // Re-load all process chains. waitForProcessChains() will only
        // re-execute those that need to be executed but will collect the output
        // of all process chains so it can be passed to the rule system.
        processChainsToResume = submissionRegistry.findProcessChainsBySubmissionId(submission.id)
      }

      // main loop
      var totalProcessChains = 0
      var errors = 0
      var results = mapOf<String, List<String>>()
      while (true) {
        // generate process chains
        val processChains = if (processChainsToResume != null) {
          val pcs = processChainsToResume
          processChainsToResume = null
          pcs
        } else {
          val pcs = ruleSystem.fire(results)
          if (pcs.isEmpty()) {
            break
          }

          // store process chains in submission registry
          submissionRegistry.addProcessChains(pcs, submission.id)

          // store the rule system's state so we are able to resume the
          // submission later if necessary
          submissionRegistry.setSubmissionExecutionState(submission.id,
              ruleSystem.persistState())

          pcs
        }

        // notify scheduler
        vertx.eventBus().send(AddressConstants.SCHEDULER_LOOKUP_NOW, null)

        // wait for process chain results
        totalProcessChains += processChains.size
        val w = waitForProcessChains(processChains)
        results = w.first
        errors += w.second
      }

      // evaluate results
      val status = if (ruleSystem.isFinished()) {
        when (errors) {
          0 -> Submission.Status.SUCCESS
          totalProcessChains -> Submission.Status.ERROR
          else -> Submission.Status.PARTIAL_SUCCESS
        }
      } else {
        log.error("Submission was not executed completely")
        Submission.Status.ERROR
      }

      val msg = "Submission `${submission.id}' finished with status $status"
      when (status) {
        Submission.Status.PARTIAL_SUCCESS -> log.warn(msg)
        Submission.Status.ERROR -> log.error(msg)
        else -> log.info(msg)
      }

      submissionRegistry.setSubmissionStatus(submission.id, status)
      submissionRegistry.setSubmissionEndTime(submission.id, Instant.now())
    } catch (t: Throwable) {
      log.error("Could not execute submission", t)
      submissionRegistry.setSubmissionStatus(submission.id, Submission.Status.ERROR)
    } finally {
      // the submission was either successful or it failed - remove the current
      // execution state so it won't be repeated/resumed
      submissionRegistry.setSubmissionExecutionState(submission.id, null)

      lock.release()
    }
  }

  /**
   * Wait for the given list of process chains to finish
   * @param processChains the process chains to wait for
   * @return a pair containing the accumulated process chain results and the
   * number of failed process chains
   */
  private suspend fun waitForProcessChains(processChains: Collection<ProcessChain>):
      Pair<Map<String, List<String>>, Int> {
    val results = mutableMapOf<String, List<String>>()
    var errors = 0

    val processChainsToCheck = processChains.map { it.id }.toMutableSet()
    while (processChainsToCheck.isNotEmpty()) {
      delay(lookupInterval)

      val finishedProcessChains = mutableSetOf<String>()
      for (processChainId in processChainsToCheck) {
        val status = submissionRegistry.getProcessChainStatus(processChainId)
        when (status) {
          ProcessChainStatus.SUCCESS -> {
            submissionRegistry.getProcessChainResults(processChainId)?.let {
              results.putAll(it)
            }
            finishedProcessChains.add(processChainId)
          }

          ProcessChainStatus.ERROR -> {
            errors++
            finishedProcessChains.add(processChainId)
          }

          else -> {}
        }
      }
      processChainsToCheck.removeAll(finishedProcessChains)
    }

    return Pair(results, errors)
  }
}
