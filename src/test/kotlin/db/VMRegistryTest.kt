package db

import assertThatThrownBy
import coVerify
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import model.cloud.VM
import model.setup.Setup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant

/**
 * Tests for all [VMRegistry] implementations
 * @author Michel Kraemer
 */
@ExtendWith(VertxExtension::class)
abstract class VMRegistryTest {
  abstract fun createRegistry(vertx: Vertx): VMRegistry

  private val setup = Setup(id = "test-setup", flavor = "myflavor",
      imageName = "myimage", availabilityZone = "my-az", blockDeviceSizeGb = 20,
      maxVMs = 10)

  private lateinit var vmRegistry: VMRegistry

  @BeforeEach
  fun setUp(vertx: Vertx) {
    vmRegistry = createRegistry(vertx)
  }

  @AfterEach
  open fun tearDown(vertx: Vertx, ctx: VertxTestContext) {
    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.close()
      ctx.completeNow()
    }
  }

  @Test
  fun addVM(vertx: Vertx, ctx: VertxTestContext) {
    val vm = VM(setup = setup)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm)
      val vm2 = vmRegistry.findVMById(vm.id)

      ctx.verify {
        assertThat(vm2).isEqualTo(vm)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findVMByIdNull(vertx: Vertx, ctx: VertxTestContext) {
    GlobalScope.launch(vertx.dispatcher()) {
      val vm = vmRegistry.findVMById("DOES_NOT_EXIST")
      ctx.verify {
        assertThat(vm).isNull()
      }
      ctx.completeNow()
    }
  }

  @Test
  fun findVMByExternalId(vertx: Vertx, ctx: VertxTestContext) {
    val vm1 = VM(setup = setup, externalId = "a")
    val vm2 = VM(setup = setup, externalId = "b")

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)
      vmRegistry.addVM(vm2)

      val r1 = vmRegistry.findVMByExternalId("a")
      val r2 = vmRegistry.findVMByExternalId("b")
      val r3 = vmRegistry.findVMByExternalId("c")
      ctx.verify {
        assertThat(r1).isEqualTo(vm1)
        assertThat(r2).isEqualTo(vm2)
        assertThat(r3).isNull()
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findVMsPage(vertx: Vertx, ctx: VertxTestContext) {
    val vm1 = VM(setup = setup, status = VM.Status.CREATING)
    val vm2 = VM(setup = setup, status = VM.Status.PROVISIONING)
    val vm3 = VM(setup = setup, status = VM.Status.RUNNING)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)
      vmRegistry.addVM(vm2)
      vmRegistry.addVM(vm3)

      // check if order is correct
      val r1 = vmRegistry.findVMs()
      ctx.verify {
        assertThat(r1).isEqualTo(listOf(vm1, vm2, vm3))
      }

      // check if order can be reversed
      val r2 = vmRegistry.findVMs(order = -1)
      ctx.verify {
        assertThat(r2).isEqualTo(listOf(vm3, vm2, vm1))
      }

      // check if we can query pages
      val r3 = vmRegistry.findVMs(size = 1, offset = 0)
      val r4 = vmRegistry.findVMs(size = 2, offset = 1)
      ctx.verify {
        assertThat(r3).isEqualTo(listOf(vm1))
        assertThat(r4).isEqualTo(listOf(vm2, vm3))
      }

      // check if we can query pages with reversed order
      val r5 = vmRegistry.findVMs(size = 1, offset = 0, order = -1)
      val r6 = vmRegistry.findVMs(size = 2, offset = 1, order = -1)
      ctx.verify {
        assertThat(r5).isEqualTo(listOf(vm3))
        assertThat(r6).isEqualTo(listOf(vm2, vm1))
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findVMsByStatus(vertx: Vertx, ctx: VertxTestContext) {
    val vm1 = VM(setup = setup, status = VM.Status.CREATING)
    val vm2 = VM(setup = setup, status = VM.Status.CREATING)
    val vm3 = VM(setup = setup, status = VM.Status.RUNNING)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)
      vmRegistry.addVM(vm2)
      vmRegistry.addVM(vm3)

      val r1 = vmRegistry.findVMs(VM.Status.CREATING)
      val r2 = vmRegistry.findVMs(VM.Status.RUNNING)
      val r3 = vmRegistry.findVMs(VM.Status.PROVISIONING)
      val r4 = vmRegistry.findVMs(VM.Status.CREATING, offset = 1, size = 1)
      val r5 = vmRegistry.findVMs(VM.Status.CREATING, offset = 1, size = 1, order = -1)
      val r6 = vmRegistry.findVMs(VM.Status.CREATING, order = -1)
      ctx.verify {
        assertThat(r1).containsExactly(vm1, vm2)
        assertThat(r2).containsExactly(vm3)
        assertThat(r3).isEmpty()
        assertThat(r4).containsExactly(vm2)
        assertThat(r5).containsExactly(vm1)
        assertThat(r6).containsExactly(vm2, vm1)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun findNonTerminatedVMs(vertx: Vertx, ctx: VertxTestContext) {
    val vm1 = VM(setup = setup, status = VM.Status.CREATING)
    val vm2 = VM(setup = setup, status = VM.Status.PROVISIONING)
    val vm3 = VM(setup = setup, status = VM.Status.RUNNING)
    val vm4 = VM(setup = setup, status = VM.Status.LEFT)
    val vm5 = VM(setup = setup, status = VM.Status.DESTROYING)
    val vm6 = VM(setup = setup, status = VM.Status.DESTROYED)
    val vm7 = VM(setup = setup, status = VM.Status.ERROR)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)
      vmRegistry.addVM(vm2)
      vmRegistry.addVM(vm3)
      vmRegistry.addVM(vm4)
      vmRegistry.addVM(vm5)
      vmRegistry.addVM(vm6)
      vmRegistry.addVM(vm7)

      val r = vmRegistry.findNonTerminatedVMs()
      ctx.verify {
        assertThat(r).containsExactlyInAnyOrder(vm1, vm2, vm3, vm4, vm5)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun countVMs(vertx: Vertx, ctx: VertxTestContext) {
    val setup2 = setup.copy(id = "another-test-setup")

    val vm1 = VM(setup = setup, status = VM.Status.CREATING)
    val vm2 = VM(setup = setup, status = VM.Status.PROVISIONING)
    val vm3 = VM(setup = setup, status = VM.Status.RUNNING)
    val vm4 = VM(setup = setup, status = VM.Status.LEFT)
    val vm5 = VM(setup = setup, status = VM.Status.DESTROYING)
    val vm6 = VM(setup = setup, status = VM.Status.DESTROYED)
    val vm7 = VM(setup = setup, status = VM.Status.ERROR)
    val vm8 = VM(setup = setup2, status = VM.Status.RUNNING)
    val vm9 = VM(setup = setup2, status = VM.Status.ERROR)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)
      vmRegistry.addVM(vm2)
      vmRegistry.addVM(vm3)
      vmRegistry.addVM(vm4)
      vmRegistry.addVM(vm5)
      vmRegistry.addVM(vm6)
      vmRegistry.addVM(vm7)
      vmRegistry.addVM(vm8)
      vmRegistry.addVM(vm9)

      val r = vmRegistry.countVMs()
      ctx.verify {
        assertThat(r).isEqualTo(9)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun countVMsByStatus(vertx: Vertx, ctx: VertxTestContext) {
    val setup2 = setup.copy(id = "another-test-setup")

    val vm1 = VM(setup = setup, status = VM.Status.CREATING)
    val vm2 = VM(setup = setup, status = VM.Status.PROVISIONING)
    val vm3 = VM(setup = setup, status = VM.Status.RUNNING)
    val vm4 = VM(setup = setup, status = VM.Status.LEFT)
    val vm5 = VM(setup = setup, status = VM.Status.DESTROYING)
    val vm6 = VM(setup = setup, status = VM.Status.DESTROYED)
    val vm7 = VM(setup = setup, status = VM.Status.ERROR)
    val vm8 = VM(setup = setup2, status = VM.Status.RUNNING)
    val vm9 = VM(setup = setup2, status = VM.Status.ERROR)
    val vm10 = VM(setup = setup, status = VM.Status.ERROR)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)
      vmRegistry.addVM(vm2)
      vmRegistry.addVM(vm3)
      vmRegistry.addVM(vm4)
      vmRegistry.addVM(vm5)
      vmRegistry.addVM(vm6)
      vmRegistry.addVM(vm7)
      vmRegistry.addVM(vm8)
      vmRegistry.addVM(vm9)
      vmRegistry.addVM(vm10)

      val r1 = vmRegistry.countVMs(VM.Status.CREATING)
      val r2 = vmRegistry.countVMs(VM.Status.PROVISIONING)
      val r3 = vmRegistry.countVMs(VM.Status.RUNNING)
      val r4 = vmRegistry.countVMs(VM.Status.LEFT)
      val r5 = vmRegistry.countVMs(VM.Status.DESTROYING)
      val r6 = vmRegistry.countVMs(VM.Status.DESTROYED)
      val r7 = vmRegistry.countVMs(VM.Status.ERROR)
      ctx.verify {
        assertThat(r1).isEqualTo(1)
        assertThat(r2).isEqualTo(1)
        assertThat(r3).isEqualTo(2)
        assertThat(r4).isEqualTo(1)
        assertThat(r5).isEqualTo(1)
        assertThat(r6).isEqualTo(1)
        assertThat(r7).isEqualTo(3)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun countNonTerminatedVMsBySetup(vertx: Vertx, ctx: VertxTestContext) {
    val setup2 = setup.copy(id = "another-test-setup")

    val vm1 = VM(setup = setup, status = VM.Status.CREATING)
    val vm2 = VM(setup = setup, status = VM.Status.PROVISIONING)
    val vm3 = VM(setup = setup, status = VM.Status.RUNNING)
    val vm4 = VM(setup = setup, status = VM.Status.LEFT)
    val vm5 = VM(setup = setup, status = VM.Status.DESTROYING)
    val vm6 = VM(setup = setup, status = VM.Status.DESTROYED)
    val vm7 = VM(setup = setup, status = VM.Status.ERROR)
    val vm8 = VM(setup = setup2, status = VM.Status.RUNNING)
    val vm9 = VM(setup = setup2, status = VM.Status.ERROR)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)
      vmRegistry.addVM(vm2)
      vmRegistry.addVM(vm3)
      vmRegistry.addVM(vm4)
      vmRegistry.addVM(vm5)
      vmRegistry.addVM(vm6)
      vmRegistry.addVM(vm7)
      vmRegistry.addVM(vm8)
      vmRegistry.addVM(vm9)

      val r1 = vmRegistry.countNonTerminatedVMsBySetup(setup.id)
      val r2 = vmRegistry.countNonTerminatedVMsBySetup(setup2.id)
      ctx.verify {
        assertThat(r1).isEqualTo(5)
        assertThat(r2).isEqualTo(1)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun countStartingVMsBySetup(vertx: Vertx, ctx: VertxTestContext) {
    val setup2 = setup.copy(id = "another-test-setup")

    val vm1 = VM(setup = setup, status = VM.Status.CREATING)
    val vm2 = VM(setup = setup, status = VM.Status.PROVISIONING)
    val vm3 = VM(setup = setup, status = VM.Status.RUNNING)
    val vm4 = VM(setup = setup, status = VM.Status.LEFT)
    val vm5 = VM(setup = setup, status = VM.Status.DESTROYING)
    val vm6 = VM(setup = setup, status = VM.Status.DESTROYED)
    val vm7 = VM(setup = setup, status = VM.Status.ERROR)
    val vm8 = VM(setup = setup2, status = VM.Status.RUNNING)
    val vm9 = VM(setup = setup2, status = VM.Status.ERROR)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)
      vmRegistry.addVM(vm2)
      vmRegistry.addVM(vm3)
      vmRegistry.addVM(vm4)
      vmRegistry.addVM(vm5)
      vmRegistry.addVM(vm6)
      vmRegistry.addVM(vm7)
      vmRegistry.addVM(vm8)
      vmRegistry.addVM(vm9)

      val r1 = vmRegistry.countStartingVMsBySetup(setup.id)
      val r2 = vmRegistry.countStartingVMsBySetup(setup2.id)
      ctx.verify {
        assertThat(r1).isEqualTo(2)
        assertThat(r2).isEqualTo(0)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setVMCreationTime(vertx: Vertx, ctx: VertxTestContext) {
    val expectedTime = Instant.now()
    val vm1 = VM(setup = setup, status = VM.Status.CREATING)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)

      val r1 = vmRegistry.findVMById(vm1.id)
      vmRegistry.setVMCreationTime(vm1.id, expectedTime)
      val r2 = vmRegistry.findVMById(vm1.id)
      ctx.verify {
        assertThat(r1).isNotNull
        assertThat(r1!!.creationTime).isNull()
        assertThat(r2).isNotNull
        assertThat(r2!!.creationTime).isEqualTo(expectedTime)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setVMAgentJoinTime(vertx: Vertx, ctx: VertxTestContext) {
    val expectedTime = Instant.now()
    val vm1 = VM(setup = setup, status = VM.Status.RUNNING)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)

      val r1 = vmRegistry.findVMById(vm1.id)
      vmRegistry.setVMAgentJoinTime(vm1.id, expectedTime)
      val r2 = vmRegistry.findVMById(vm1.id)
      ctx.verify {
        assertThat(r1).isNotNull
        assertThat(r1!!.agentJoinTime).isNull()
        assertThat(r2).isNotNull
        assertThat(r2!!.agentJoinTime).isEqualTo(expectedTime)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setVMDestructionTime(vertx: Vertx, ctx: VertxTestContext) {
    val expectedTime = Instant.now()
    val vm1 = VM(setup = setup, status = VM.Status.DESTROYED)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)

      val r1 = vmRegistry.findVMById(vm1.id)
      vmRegistry.setVMDestructionTime(vm1.id, expectedTime)
      val r2 = vmRegistry.findVMById(vm1.id)
      ctx.verify {
        assertThat(r1).isNotNull
        assertThat(r1!!.destructionTime).isNull()
        assertThat(r2).isNotNull
        assertThat(r2!!.destructionTime).isEqualTo(expectedTime)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setVMStatus(vertx: Vertx, ctx: VertxTestContext) {
    val vm1 = VM(setup = setup, status = VM.Status.CREATING)
    val vm2 = VM(setup = setup, status = VM.Status.PROVISIONING)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)
      vmRegistry.addVM(vm2)

      val r11 = vmRegistry.findVMById(vm1.id)
      val r12 = vmRegistry.findVMById(vm2.id)
      ctx.verify {
        assertThat(r11!!.status).isEqualTo(VM.Status.CREATING)
        assertThat(r12!!.status).isEqualTo(VM.Status.PROVISIONING)
      }

      vmRegistry.setVMStatus(vm1.id, VM.Status.CREATING, VM.Status.PROVISIONING)
      val r21 = vmRegistry.findVMById(vm1.id)
      val r22 = vmRegistry.findVMById(vm2.id)
      ctx.verify {
        assertThat(r21!!.status).isEqualTo(VM.Status.PROVISIONING)
        assertThat(r22!!.status).isEqualTo(VM.Status.PROVISIONING)
      }

      vmRegistry.setVMStatus(vm1.id, VM.Status.CREATING, VM.Status.ERROR)
      val r31 = vmRegistry.findVMById(vm1.id)
      val r32 = vmRegistry.findVMById(vm2.id)
      ctx.verify {
        assertThat(r31!!.status).isEqualTo(VM.Status.PROVISIONING)
        assertThat(r32!!.status).isEqualTo(VM.Status.PROVISIONING)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun forceSetVMStatus(vertx: Vertx, ctx: VertxTestContext) {
    val vm1 = VM(setup = setup, status = VM.Status.CREATING)
    val vm2 = VM(setup = setup, status = VM.Status.PROVISIONING)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)
      vmRegistry.addVM(vm2)

      vmRegistry.forceSetVMStatus(vm1.id, VM.Status.PROVISIONING)
      val r11 = vmRegistry.findVMById(vm1.id)
      val r12 = vmRegistry.findVMById(vm2.id)
      ctx.verify {
        assertThat(r11!!.status).isEqualTo(VM.Status.PROVISIONING)
        assertThat(r12!!.status).isEqualTo(VM.Status.PROVISIONING)
      }

      vmRegistry.forceSetVMStatus(vm1.id, VM.Status.ERROR)
      val r21 = vmRegistry.findVMById(vm1.id)
      val r22 = vmRegistry.findVMById(vm2.id)
      ctx.verify {
        assertThat(r21!!.status).isEqualTo(VM.Status.ERROR)
        assertThat(r22!!.status).isEqualTo(VM.Status.PROVISIONING)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun getVMStatus(vertx: Vertx, ctx: VertxTestContext) {
    val vm1 = VM(setup = setup, status = VM.Status.CREATING)
    val vm2 = VM(setup = setup, status = VM.Status.PROVISIONING)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)
      vmRegistry.addVM(vm2)

      val r11 = vmRegistry.getVMStatus(vm1.id)
      val r12 = vmRegistry.getVMStatus(vm2.id)
      ctx.verify {
        assertThat(r11).isEqualTo(VM.Status.CREATING)
        assertThat(r12).isEqualTo(VM.Status.PROVISIONING)
      }

      vmRegistry.forceSetVMStatus(vm1.id, VM.Status.ERROR)
      val r21 = vmRegistry.getVMStatus(vm1.id)
      val r22 = vmRegistry.getVMStatus(vm2.id)
      ctx.verify {
        assertThat(r21).isEqualTo(VM.Status.ERROR)
        assertThat(r22).isEqualTo(VM.Status.PROVISIONING)
      }

      ctx.coVerify {
        assertThatThrownBy { vmRegistry.getVMStatus("FOOBAR") }
            .isInstanceOf(NoSuchElementException::class.java)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setVMExternalID(vertx: Vertx, ctx: VertxTestContext) {
    val expectedId = "external-id"
    val vm1 = VM(setup = setup, status = VM.Status.CREATING)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)

      vmRegistry.setVMExternalID(vm1.id, expectedId)
      val r1 = vmRegistry.findVMById(vm1.id)
      ctx.verify {
        assertThat(r1).isNotNull
        assertThat(r1!!.externalId).isEqualTo(expectedId)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setVMIPAddress(vertx: Vertx, ctx: VertxTestContext) {
    val expectedIpAddress = "192.168.0.1"
    val vm1 = VM(setup = setup, status = VM.Status.CREATING)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)

      vmRegistry.setVMIPAddress(vm1.id, expectedIpAddress)
      val r1 = vmRegistry.findVMById(vm1.id)
      ctx.verify {
        assertThat(r1).isNotNull
        assertThat(r1!!.ipAddress).isEqualTo(expectedIpAddress)
      }

      ctx.completeNow()
    }
  }

  @Test
  fun setVMReason(vertx: Vertx, ctx: VertxTestContext) {
    val expectedReason = "THIS IS AN ERROR"
    val vm1 = VM(setup = setup, status = VM.Status.CREATING)

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)

      val r1 = vmRegistry.findVMById(vm1.id)
      ctx.verify {
        assertThat(r1).isNotNull
        assertThat(r1!!.reason).isNull()
      }

      vmRegistry.setVMReason(vm1.id, expectedReason)
      val r2 = vmRegistry.findVMById(vm1.id)
      ctx.verify {
        assertThat(r2).isNotNull
        assertThat(r2!!.reason).isEqualTo(expectedReason)
      }

      vmRegistry.setVMReason(vm1.id, null)
      val r3 = vmRegistry.findVMById(vm1.id)
      ctx.verify {
        assertThat(r3).isNotNull
        assertThat(r3!!.reason).isNull()
      }

      ctx.completeNow()
    }
  }

  @Test
  fun deleteVMsDestroyedBefore(vertx: Vertx, ctx: VertxTestContext) {
    val vm1 = VM(setup = setup, status = VM.Status.CREATING)
    val vm2 = VM(setup = setup, status = VM.Status.RUNNING)
    val vm3 = VM(setup = setup, status = VM.Status.DESTROYED)
    val vm4 = VM(setup = setup, status = VM.Status.DESTROYING)
    val vm5 = VM(setup = setup, status = VM.Status.DESTROYED)
    val vm6 = VM(setup = setup, status = VM.Status.ERROR)
    val vm7 = VM(setup = setup, status = VM.Status.LEFT)
    val vm8 = VM(setup = setup, status = VM.Status.DESTROYED)

    val now = Instant.now()

    GlobalScope.launch(vertx.dispatcher()) {
      vmRegistry.addVM(vm1)
      vmRegistry.addVM(vm2)
      vmRegistry.addVM(vm3)
      vmRegistry.addVM(vm4)
      vmRegistry.addVM(vm5)
      vmRegistry.addVM(vm6)
      vmRegistry.addVM(vm7)
      vmRegistry.addVM(vm8)

      vmRegistry.setVMDestructionTime(vm3.id, now)
      vmRegistry.setVMDestructionTime(vm5.id, now.minusSeconds(100))
      vmRegistry.setVMDestructionTime(vm6.id, now.minusSeconds(200))
      vmRegistry.setVMDestructionTime(vm8.id, now.minusSeconds(50))

      vmRegistry.deleteVMsDestroyedBefore(now.minusSeconds(50))

      val r = vmRegistry.findVMs().map { it.id }
      ctx.verify {
        assertThat(r).containsExactlyInAnyOrder(vm1.id, vm2.id, vm3.id, vm4.id, vm7.id, vm8.id)
      }

      ctx.completeNow()
    }
  }
}
