package db

import helper.JsonUtils
import io.vertx.core.Vertx
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.ext.sql.queryAwait
import io.vertx.kotlin.ext.sql.querySingleAwait
import io.vertx.kotlin.ext.sql.querySingleWithParamsAwait
import io.vertx.kotlin.ext.sql.queryWithParamsAwait
import io.vertx.kotlin.ext.sql.updateWithParamsAwait
import model.cloud.VM
import java.lang.StringBuilder
import java.time.Instant

/**
 * A VM registry that keeps objects in a PostgreSQL database
 * @param vertx the current Vert.x instance
 * @param url the JDBC url to the database
 * @param username the username
 * @param password the password
 * @author Michel Kraemer
 */
class PostgreSQLVMRegistry(private val vertx: Vertx, url: String,
    username: String, password: String) : PostgreSQLRegistry(vertx, url, username, password),
    VMRegistry {
  companion object {
    /**
     * Table and column names
     */
    private const val VMS = "vms"
    private const val SERIAL = "serial"
    private const val EXTERNAL_ID = "externalId"
    private const val IP_ADDRESS = "ipAddress"
    private const val SETUP = "setup"
    private const val CREATION_TIME = "creationTime"
    private const val AGENT_JOIN_TIME = "agentJoinTime"
    private const val DESTRUCTION_TIME = "destructionTime"
    private const val STATUS = "status"
    private const val REASON = "reason"
  }

  override suspend fun addVM(vm: VM) {
    withConnection { connection ->
      val statement = "INSERT INTO $VMS ($ID, $DATA) VALUES (?, ?::jsonb)"
      val params = json {
        array(
            vm.id,
            JsonUtils.writeValueAsString(vm)
        )
      }
      connection.updateWithParamsAwait(statement, params)
    }
  }

  override suspend fun findVMs(status: VM.Status?, size: Int, offset: Int,
      order: Int): Collection<VM> {
    val asc = if (order >= 0) "ASC" else "DESC"
    val limit = if (size < 0) "ALL" else size.toString()
    return withConnection { connection ->
      val statement = StringBuilder("SELECT $DATA FROM $VMS ")

      val params = if (status != null) {
        statement.append("WHERE $DATA->'$STATUS'=?::jsonb ")
        json {
          array(
              "\"$status\""
          )
        }
      } else {
        null
      }

      statement.append("ORDER BY $SERIAL $asc LIMIT $limit OFFSET $offset")

      val rs = if (params == null) {
        connection.queryAwait(statement.toString())
      } else {
        connection.queryWithParamsAwait(statement.toString(), params)
      }
      rs.results.map { JsonUtils.readValue(it.getString(0)) }
    }
  }

  override suspend fun findVMById(id: String): VM? {
    return withConnection { connection ->
      val statement = "SELECT $DATA FROM $VMS WHERE $ID=?"
      val params = json {
        array(
            id
        )
      }
      val rs = connection.querySingleWithParamsAwait(statement, params)
      rs?.let { JsonUtils.readValue<VM>(it.getString(0)) }
    }
  }

  override suspend fun findVMByExternalId(externalId: String): VM? {
    return withConnection { connection ->
      val statement = "SELECT $DATA FROM $VMS WHERE $DATA->'$EXTERNAL_ID'=?::jsonb"
      val params = json {
        array(
            JsonUtils.writeValueAsString(externalId)
        )
      }
      val rs = connection.querySingleWithParamsAwait(statement, params)
      rs?.let { JsonUtils.readValue<VM>(it.getString(0)) }
    }
  }

  override suspend fun findNonTerminatedVMs(): Collection<VM> {
    return withConnection { connection ->
      val statement = "SELECT $DATA FROM $VMS WHERE $DATA->'$STATUS'!=?::jsonb " +
          "AND $DATA->'$STATUS'!=?::jsonb"
      val params = json {
        array(
            "\"${VM.Status.DESTROYED}\"",
            "\"${VM.Status.ERROR}\""
        )
      }
      val rs = connection.queryWithParamsAwait(statement, params)
      rs.results.map { JsonUtils.readValue(it.getString(0)) }
    }
  }

  override suspend fun countVMs(status: VM.Status?): Long {
    return withConnection { connection ->
      val statement = StringBuilder("SELECT COUNT(*) FROM $VMS")

      val params = if (status != null) {
        statement.append(" WHERE $DATA->'$STATUS'=?::jsonb")
        json {
          array(
              "\"$status\""
          )
        }
      } else {
        null
      }

      val rs = if (params == null) {
        connection.querySingleAwait(statement.toString())
      } else {
        connection.querySingleWithParamsAwait(statement.toString(), params)
      }
      rs?.getLong(0) ?: 0L
    }
  }

  override suspend fun countNonTerminatedVMsBySetup(setupId: String): Long {
    return withConnection { connection ->
      val statement = "SELECT COUNT(*) FROM $VMS WHERE $DATA->'$SETUP'->'$ID'=?::jsonb " +
          "AND $DATA->'$STATUS'!=?::jsonb AND $DATA->'$STATUS'!=?::jsonb"
      val params = json {
        array(
            JsonUtils.writeValueAsString(setupId),
            "\"${VM.Status.DESTROYED}\"",
            "\"${VM.Status.ERROR}\""
        )
      }
      val rs = connection.querySingleWithParamsAwait(statement, params)
      rs?.getLong(0) ?: 0L
    }
  }

  override suspend fun countStartingVMsBySetup(setupId: String): Long {
    return withConnection { connection ->
      val statement = "SELECT COUNT(*) FROM $VMS WHERE $DATA->'$SETUP'->'$ID'=?::jsonb " +
          "AND ($DATA->'$STATUS'=?::jsonb OR $DATA->'$STATUS'=?::jsonb)"
      val params = json {
        array(
            JsonUtils.writeValueAsString(setupId),
            "\"${VM.Status.CREATING}\"",
            "\"${VM.Status.PROVISIONING}\""
        )
      }
      val rs = connection.querySingleWithParamsAwait(statement, params)
      rs?.getLong(0) ?: 0L
    }
  }

  override suspend fun setVMCreationTime(id: String, creationTime: Instant) {
    val newObj = json {
      obj(
          CREATION_TIME to creationTime
      )
    }
    updateProperties(VMS, id, newObj)
  }

  override suspend fun setVMAgentJoinTime(id: String, agentJoinTime: Instant) {
    val newObj = json {
      obj(
          AGENT_JOIN_TIME to agentJoinTime
      )
    }
    updateProperties(VMS, id, newObj)
  }

  override suspend fun setVMDestructionTime(id: String, destructionTime: Instant) {
    val newObj = json {
      obj(
          DESTRUCTION_TIME to destructionTime
      )
    }
    updateProperties(VMS, id, newObj)
  }

  override suspend fun setVMStatus(id: String, currentStatus: VM.Status,
      newStatus: VM.Status) {
    withConnection { connection ->
      val updateStatement = "UPDATE $VMS SET $DATA=$DATA || ?::jsonb WHERE $ID=? " +
          "AND $DATA->'$STATUS'=?::jsonb"
      val newObj = json {
        obj(
            STATUS to newStatus.toString()
        )
      }
      val updateParams = json {
        array(
            newObj.encode(),
            id,
            "\"$currentStatus\""
        )
      }
      connection.updateWithParamsAwait(updateStatement, updateParams)
    }
  }

  override suspend fun forceSetVMStatus(id: String, newStatus: VM.Status) {
    val newObj = json {
      obj(
          STATUS to newStatus.toString()
      )
    }
    updateProperties(VMS, id, newObj)
  }

  override suspend fun getVMStatus(id: String): VM.Status {
    return withConnection { connection ->
      val statement = "SELECT $DATA->'$STATUS' FROM $VMS WHERE $ID=?"
      val params = json {
        array(
            id
        )
      }
      val rs = connection.querySingleWithParamsAwait(statement, params) ?:
          throw NoSuchElementException("There is no VM with ID `$id'")
      VM.Status.valueOf(JsonUtils.readValue(rs.getString(0)))
    }
  }

  override suspend fun setVMExternalID(id: String, externalId: String) {
    val newObj = json {
      obj(
          EXTERNAL_ID to externalId
      )
    }
    updateProperties(VMS, id, newObj)
  }

  override suspend fun setVMIPAddress(id: String, ipAddress: String) {
    val newObj = json {
      obj(
          IP_ADDRESS to ipAddress
      )
    }
    updateProperties(VMS, id, newObj)
  }

  override suspend fun setVMReason(id: String, reason: String?) {
    val newObj = json {
      obj(
          REASON to reason
      )
    }
    updateProperties(VMS, id, newObj)
  }
}
