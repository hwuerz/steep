package db

import io.vertx.core.Vertx
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.jdbc.spi.impl.HikariCPDataSourceProvider
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.ext.sql.closeAwait
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.testcontainers.jdbc.ContainerDatabaseDriver

/**
 * Common code for all tests that need a PostgreSQL database
 * @author MicheL Kraemer
 */
interface PostgreSQLTest {
  companion object {
    /**
     * Use Testcontainers' feature to automatically create a container for a
     * JDBC database if you add the tc: prefix to the URL. Use TC_DAEMON=true
     * to keep the container running until all tests have finished
     */
    const val URL = "jdbc:tc:postgresql:10.5://hostname/databasename?TC_DAEMON=true"

    /**
     * Kill the test container automatically created by Testcontainers as soon
     * as all tests have finished
     */
    @AfterAll
    @JvmStatic
    @Suppress("UNUSED")
    fun shutdown() {
      ContainerDatabaseDriver.killContainer(URL)
    }
  }

  /**
   * Clear database after each test
   */
  @AfterEach
  fun tearDownDatabase(vertx: Vertx, ctx: VertxTestContext) {
    val jdbcConfig = json {
      obj(
          "provider_class" to HikariCPDataSourceProvider::class.java.name,
          "jdbcUrl" to URL,
          "username" to "user",
          "password" to "password"
      )
    }
    val client = JDBCClient.createShared(vertx, jdbcConfig)

    GlobalScope.launch(vertx.dispatcher()) {
      deleteFromTables(client)
      client.closeAwait()
      ctx.completeNow()
    }

    // make sure migrations will run for the next unit test
    PostgreSQLRegistry.migratedDatabases.clear()
  }

  /**
   * Will be called after each test to clean up all tables
   */
  suspend fun deleteFromTables(client: JDBCClient)
}
