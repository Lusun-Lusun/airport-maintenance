package airport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "airport.outbox.delay=3600000"
    })
@AutoConfigureMockMvc
class WorkflowTest {
  @Autowired MaintenanceService service;
  @Autowired Store db;
  @Autowired JdbcTemplate jdbc;
  @Autowired OutboxWorker worker;
  @Autowired MockMvc mvc;

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM notification");
    jdbc.update("DELETE FROM work_order");
    jdbc.update("DELETE FROM outbox");
    jdbc.update("DELETE FROM inspection");
    jdbc.update("DELETE FROM audit_log");
    jdbc.update("UPDATE technician SET active=TRUE,capacity=3");
    jdbc.update("UPDATE equipment SET status='正常'");
  }

  long inspect(long eid) {
    return ((Number) service.inspect(UUID.randomUUID().toString(), eid, true, "异常").get("id"))
        .longValue();
  }

  long deliver(long iid) {
    long msg =
        ((Number)
                db.pending().stream()
                    .filter(m -> ((Number) m.get("id")).longValue() == iid)
                    .findFirst()
                    .orElseThrow()
                    .get("message_id"))
            .longValue();
    service.deliver(msg, false);
    return ((Number) db.byInspection(iid).get("id")).longValue();
  }

  @Test
  void normalInspectionCreatesNoMessage() {
    service.inspect("normal", 1, false, "正常");
    assertEquals(1, db.inspections().size());
    assertTrue(db.messages().isEmpty());
    assertTrue(db.orders().isEmpty());
  }

  @Test
  void inspectionIdempotencyAndConflict() {
    var a = service.inspect("same", 1, true, "异常");
    var b = service.inspect("same", 1, true, "异常");
    assertEquals(a.get("id"), b.get("id"));
    assertEquals(1, db.pending().size());
    assertThrows(Exception.class, () -> service.inspect("same", 2, true, "不同"));
  }

  @Test
  void invalidEquipmentRollsBack() {
    assertThrows(Exception.class, () -> service.inspect("bad", 9999, true, "异常"));
    assertTrue(db.inspections().isEmpty());
    assertTrue(db.messages().isEmpty());
  }

  @Test
  void outboxFailureRetryAndDuplicateDelivery() {
    long iid = inspect(4);
    long msg = ((Number) db.pending().get(0).get("message_id")).longValue();
    assertThrows(IllegalStateException.class, () -> service.deliver(msg, true));
    assertNull(db.byInspection(iid));
    assertEquals(1, db.pending().size());
    db.failed(msg, "injected");
    worker.run();
    worker.run();
    service.deliver(msg, false);
    assertEquals(1, db.orders().size());
    assertTrue(db.pending().isEmpty());
    assertEquals(2, ((Number) db.messages().get(0).get("attempts")).intValue());
    assertEquals("P1", db.byInspection(iid).get("priority"));
  }

  @Test
  void exhaustedCapacityEscalatesAndNotifies() {
    jdbc.update("UPDATE technician SET capacity=0");
    long id = deliver(inspect(1));
    assertEquals("ESCALATED", db.order(id).get("status"));
    assertEquals(1, db.notifications().size());
    assertNull(db.order(id).get("technician_id"));
  }

  @Test
  void completeRepairAcceptanceLifecycle() {
    long id = deliver(inspect(1));
    assertEquals("ASSIGNED", db.order(id).get("status"));
    assertThrows(Exception.class, () -> service.action(id, "accept", "越级验收", null));
    service.action(id, "start", "", null);
    assertThrows(Exception.class, () -> service.action(id, "repair", "", null));
    service.action(id, "repair", "更换轴承", null);
    service.action(id, "reject", "振动复测不通过", null);
    assertNull(db.order(id).get("repaired_at"));
    service.action(id, "repair", "重新校准", null);
    service.action(id, "accept", "验收通过", null);
    assertEquals("CLOSED", db.order(id).get("status"));
    assertEquals("正常", db.equipmentOne(1).get("status"));
    assertEquals(7, db.auditLogs().size());
  }

  @Test
  void cannotAssignUnqualifiedTechnician() {
    long id = deliver(inspect(1));
    assertThrows(Exception.class, () -> service.action(id, "assign", "", 2L));
    assertThrows(Exception.class, () -> service.action(id, "assign", "", 999L));
  }

  @Test
  void matchingManualAssignmentRecoversEscalation() {
    jdbc.update("UPDATE technician SET active=FALSE");
    long id = deliver(inspect(1));
    jdbc.update("UPDATE technician SET active=TRUE WHERE id=1");
    service.action(id, "assign", "主管指派", 1L);
    assertEquals("ASSIGNED", db.order(id).get("status"));
  }

  @Test
  void acceptingOneFaultKeepsOtherFaultActive() {
    long a = deliver(inspect(1));
    deliver(inspect(1));
    service.action(a, "start", "", null);
    service.action(a, "repair", "修复", null);
    service.action(a, "accept", "通过", null);
    assertEquals("故障", db.equipmentOne(1).get("status"));
  }

  @Test
  void overlappingDowntimeMergedAndClipped() {
    long a = deliver(inspect(1));
    long b = deliver(inspect(1));
    jdbc.update(
        "UPDATE work_order SET opened_at='2026-08-01 00:00:00',repaired_at='2026-08-01"
            + " 02:00:00',accepted_at='2026-08-01 04:00:00',status='CLOSED' WHERE id=?",
        a);
    jdbc.update(
        "UPDATE work_order SET opened_at='2026-08-01 03:00:00',repaired_at='2026-08-01"
            + " 06:00:00',accepted_at='2026-08-01 08:00:00',status='CLOSED' WHERE id=?",
        b);
    var s =
        service.stats(Instant.parse("2026-08-01T01:00:00Z"), Instant.parse("2026-08-02T01:00:00Z"));
    assertEquals(7.0, (double) s.get("downtimeHours"), 1e-9);
    assertEquals(2.5, (double) s.get("mttrHours"), 1e-9);
    assertEquals(100 * (1 - 7.0 / (30 * 24)), (double) s.get("availabilityPercent"), 1e-9);
  }

  @Test
  void pendingFaultIncludedBeforeOrderExists() {
    inspect(1);
    jdbc.update("UPDATE inspection SET created_at='2026-08-01 00:00:00'");
    var s =
        service.stats(Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"));
    assertEquals(24.0, (double) s.get("downtimeHours"), 1e-9);
    assertNull(s.get("mttrHours"));
  }

  @Test
  void invalidWindowRejected() {
    assertThrows(
        Exception.class, () -> service.stats(Instant.now(), Instant.parse("2026-01-01T00:00:00Z")));
  }

  @Test
  void apiValidationAndEndpoints() throws Exception {
    mvc.perform(post("/api/inspections").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/api/equipment"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(30));
    mvc.perform(
            post("/api/equipment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"BHS-001\",\"name\":\"重复\",\"system\":\"测试\",\"area\":\"T1\",\"skill\":\"机械\",\"criticality\":2}"))
        .andExpect(status().isConflict());
  }
}
