package airport;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class Api {
  final Store db;
  final MaintenanceService service;
  final OutboxWorker worker;
  final StringRedisTemplate redis;
  final ObjectMapper json;

  @Value("${airport.redis.enabled:false}")
  boolean cache;

  public Api(
      Store db,
      MaintenanceService service,
      OutboxWorker worker,
      StringRedisTemplate redis,
      ObjectMapper json) {
    this.db = db;
    this.service = service;
    this.worker = worker;
    this.redis = redis;
    this.json = json;
  }

  record Inspection(
      @NotBlank @Size(max = 100) String requestKey,
      @Positive long equipmentId,
      boolean abnormal,
      @NotBlank @Size(max = 2000) String note) {}

  record Action(@NotBlank String action, @Size(max = 2000) String note, Long technicianId) {}

  record Equipment(
      @NotBlank @Size(max = 80) String code,
      @NotBlank @Size(max = 150) String name,
      @NotBlank @Size(max = 80) String system,
      @NotBlank @Size(max = 80) String area,
      @NotBlank @Size(max = 80) String skill,
      @Min(1) @Max(3) int criticality,
      boolean affectsFlights) {}

  @GetMapping("/equipment")
  Object equipment() {
    return db.equipment();
  }

  @PostMapping("/equipment")
  Object equipment(@Valid @RequestBody Equipment e) {
    db.addEquipment(
        Map.of(
            "code",
            e.code(),
            "name",
            e.name(),
            "system",
            e.system(),
            "area",
            e.area(),
            "skill",
            e.skill(),
            "criticality",
            e.criticality(),
            "affectsFlights",
            e.affectsFlights()));
    db.audit("EQUIPMENT_CREATED", e.code());
    return Map.of("ok", true);
  }

  @GetMapping("/technicians")
  Object technicians() {
    return db.technicians();
  }

  @GetMapping("/orders")
  Object orders() {
    return db.orders();
  }

  @GetMapping("/inspections")
  Object inspections() {
    return db.inspections();
  }

  @PostMapping("/inspections")
  Object inspect(@Valid @RequestBody Inspection i) {
    return service.inspect(i.requestKey(), i.equipmentId(), i.abnormal(), i.note());
  }

  @PostMapping("/orders/{id}/action")
  Object action(@PathVariable long id, @Valid @RequestBody Action a) {
    service.action(id, a.action(), a.note() == null ? "" : a.note(), a.technicianId());
    return Map.of("ok", true);
  }

  @GetMapping("/messages")
  Object messages() {
    return db.messages();
  }

  @PostMapping("/messages/retry")
  Object retry() {
    worker.run();
    return Map.of("pending", db.pending().size());
  }

  @GetMapping("/notifications")
  Object notifications() {
    return db.notifications();
  }

  @PostMapping("/notifications/{id}/read")
  Object read(@PathVariable long id) {
    return Map.of("updated", db.read(id));
  }

  @GetMapping("/audit")
  Object audit() {
    return db.auditLogs();
  }

  @GetMapping("/health")
  Object health() {
    return Map.of(
        "status", "UP", "cache", cache ? "Redis（失败自动直查）" : "直查数据库", "dataset", "固定种子合成数据，非机场实测");
  }

  @GetMapping("/stats")
  Object stats(
      @RequestParam(defaultValue = "2026-08-01T00:00:00Z") Instant from,
      @RequestParam(required = false) Instant to) {
    Instant end = to == null ? Instant.now() : to;
    if (cache && to != null) {
      String key = "airport:stats:" + from + ":" + end;
      try {
        String value = redis.opsForValue().get(key);
        if (value != null) return json.readTree(value);
      } catch (Exception ignored) {
      }
      var result = service.stats(from, end);
      try {
        redis.opsForValue().set(key, json.writeValueAsString(result), Duration.ofSeconds(10));
      } catch (Exception ignored) {
      }
      return result;
    }
    return service.stats(from, end);
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  @ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
  Object conflict() {
    return Map.of("message", "数据冲突：编号重复或关联数据不合法");
  }
}
