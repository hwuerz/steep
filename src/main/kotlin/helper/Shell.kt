package helper

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Provides methods to execute shell commands
 * @author Michel Kraemer
 */
object Shell {
  private val log = LoggerFactory.getLogger(Shell::class.java)

  /**
   * Executes the given command
   * @param command the command
   * @param outputCollector collects the command's output (stdout and stderr)
   * @param logFailedExitCode `true` if a failed process with a non-zero exit
   * code should be logged
   */
  fun execute(command: List<String>, outputCollector: OutputCollector = DefaultOutputCollector(),
      logFailedExitCode: Boolean = true) {
    return execute(command, File(System.getProperty("user.dir")),
        outputCollector, logFailedExitCode)
  }

  /**
   * Executes the given command in the given working directory
   * @param command the command
   * @param workingDir the working directory
   * @param outputCollector collects the command's output (stdout and stderr)
   * @param logFailedExitCode `true` if a failed process with a non-zero exit
   * code should be logged
   * @throws ExecutionException if the command failed
   */
  private fun execute(command: List<String>, workingDir: File,
      outputCollector: OutputCollector = DefaultOutputCollector(),
      logFailedExitCode: Boolean = true) {
    val joinedCommand = command.joinToString(" ")
    log.info(joinedCommand)

    val process = ProcessBuilder(command)
        .directory(workingDir)
        .redirectErrorStream(true)
        .start()

    val streamGobbler = StreamGobbler(process.pid(), process.inputStream,
        outputCollector, MDC.getCopyOfContextMap())
    val readerThread = Thread(streamGobbler)
    readerThread.start()

    try {
      process.waitFor()
    } catch (e: InterruptedException) {
      // Always destroy process forcibly because it seems Docker containers
      // cannot be destroyed normally. If necessary, we could make this
      // configurable (for example through service metadata) but for the time
      // being, there's no need.
      // process.destroy()
      // if (!process.waitFor(10, TimeUnit.SECONDS)) {
      //  log.warn("Unable to destroy process after 10s. Trying to destroy forcibly ...")
        process.destroyForcibly()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
          log.error("Unable to forcibly destroy process")
          throw InterruptedException("Unable to forcibly destroy process")
        }
      // }
      throw e
    } finally {
      readerThread.join()
    }

    val code = process.exitValue()
    if (code != 0) {
      if (logFailedExitCode) {
        log.error("Command failed with exit code: $code")
      }
      throw ExecutionException("Failed to run `$joinedCommand'",
          outputCollector.output(), code)
    }
  }

  /**
   * An exception thrown by [execute] if the command failed
   * @param message a generic error message
   * @param lastOutput the last output collected from the command before it
   * failed (may contain the actual error message issued by the command)
   * @param exitCode the command's exit code
   */
  class ExecutionException(
      message: String,
      val lastOutput: String,
      val exitCode: Int
  ) : IOException(message)

  /**
   * A background thread that reads all lines from the [inputStream] of a process
   * with the given [pid] and collects them in an [outputCollector]
   */
  private class StreamGobbler(private val pid: Long,
      private val inputStream: InputStream,
      private val outputCollector: OutputCollector,
      private val mdc: Map<String, String>?) : Runnable {
    override fun run() {
      mdc?.let { MDC.setContextMap(it) }
      inputStream.bufferedReader().forEachLine { line ->
        log.info("[$pid] $line")
        outputCollector.collect(line)
      }
    }
  }
}
