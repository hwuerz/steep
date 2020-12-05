package db

import com.mongodb.ConnectionString
import com.mongodb.reactivestreams.client.MongoClients
import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodProcess
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.MongodConfig
import de.flapdoodle.embed.mongo.distribution.Version
import helper.dropAwait
import io.vertx.core.Vertx
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll

/**
 * Common code for all tests that need a MongoDB instance
 * @author MicheL Kraemer
 */
interface MongoDBTest {
  companion object {
    private val STARTER = MongodStarter.getDefaultInstance()

    private lateinit var MONGOD_EXE: MongodExecutable
    private lateinit var MONGOD: MongodProcess
    private val MONGOD_CONFIG = MongodConfig.builder()
        .version(Version.Main.PRODUCTION)
        .build()
    val CONNECTION_STRING = "mongodb://" +
        MONGOD_CONFIG.net().serverAddress.hostAddress + ":" +
        MONGOD_CONFIG.net().port + "/steep"

    @BeforeAll
    @JvmStatic
    @Suppress("UNUSED")
    fun startUp() {
      MONGOD_EXE = STARTER.prepare(MONGOD_CONFIG)
      MONGOD = MONGOD_EXE.start()
    }

    @AfterAll
    @JvmStatic
    @Suppress("UNUSED")
    fun shutdown() {
      MONGOD.stop()
      MONGOD_EXE.stop()
    }
  }

  @AfterEach
  fun tearDownDatabase(vertx: Vertx, ctx: VertxTestContext) {
    val cs = ConnectionString(CONNECTION_STRING)
    val client = MongoClients.create(cs)

    GlobalScope.launch(vertx.dispatcher()) {
      client.getDatabase(cs.database).dropAwait()
      client.close()
      ctx.completeNow()
    }
  }
}
