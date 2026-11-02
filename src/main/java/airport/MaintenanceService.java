package airport;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MaintenanceService {
  final Store db;

  public MaintenanceService(Store db) {
    this.db = db;
  }

  static long n(Object o) {
    return ((Number) o).longValue();
  }

  static void require(boolean b, String m) {
    if (!b) throw new ResponseStatusException(HttpStatus.CONFLICT, m);
  }

  @Transactional
  public synchronized Map<String, Object> inspect(
      String key, long equipmentId, boolean abnormal, String note) {
    var old = db.inspectionByKey(key);
    if (old != null) {
      require(
          n(old.get("equipment_id")) == equipmentId
              && old.get("abnormal").equals(abnormal)
              && Objects.equals(old.get("note"), note),
          "幂等键已用于不同内容");
      return old;
    }
    require(db.equipmentOne(equipmentId) != null, "设备不存在");
    db.inspection(
        Map.of("key", key, "equipmentId", equipmentId, "abnormal", abnormal, "note", note));
    var saved = db.inspectionByKey(key);
    if (abnormal) {
      db.enqueue(n(saved.get("id")));
      db.equipmentStatus(equipmentId, "故障");
    }
    db.audit("INSPECTION", "inspection=" + saved.get("id"));
    return saved;
  }

  @Transactional
  public synchronized void deliver(long messageId, boolean injectFailure) {
    db.lockDispatch();
    var msg = db.pending().stream().filter(m -> n(m.get("message_id")) == messageId).findFirst();
    if (msg.isEmpty()) return;
    var m = msg.get();
    if (injectFailure) throw new IllegalStateException("测试注入：工单写入前故障");
    long iid = n(m.get("id"));
    if (db.byInspection(iid) == null) {
      var e = db.equipmentOne(n(m.get("equipment_id")));
      var t = Rules.recommend(e, db.technicians());
      Map<String, Object> p = new HashMap<>();
      p.put("inspectionId", iid);
      p.put("equipmentId", e.get("id"));
      p.put(
          "priority",
          Rules.priority(
              ((Number) e.get("criticality")).intValue(), (Boolean) e.get("affects_flights")));
      p.put("technicianId", t.map(a -> a.get("id")).orElse(null));
      p.put("status", t.isPresent() ? "ASSIGNED" : "ESCALATED");
      p.put("openedAt", m.get("created_at"));
      db.createOrder(p);
      var w = db.byInspection(iid);
      if (t.isEmpty()) db.notify(n(w.get("id")), "无符合技能、区域与负载要求的人员，请处理升级工单 #" + w.get("id"));
      db.audit("ORDER_CREATED", "order=" + w.get("id"));
    }
    db.done(messageId);
  }

  @Transactional
  public synchronized void action(long id, String action, String note, Long technicianId) {
    db.lockDispatch();
    db.lockOrder(id);
    var w = db.order(id);
    require(w != null, "工单不存在");
    String s = w.get("status").toString();
    switch (action) {
      case "assign" -> {
        require(Set.of("ESCALATED", "ASSIGNED").contains(s), "当前状态不可派单");
        var e = db.equipmentOne(n(w.get("equipment_id")));
        var t =
            db.technicians().stream()
                .filter(a -> n(a.get("id")) == Objects.requireNonNullElse(technicianId, -1L))
                .findFirst();
        require(t.isPresent(), "人员不存在");
        var candidates = List.of(t.get());
        if (Objects.equals(w.get("technician_id"), technicianId)) {
          candidates = new ArrayList<>();
          var copy = new HashMap<>(t.get());
          copy.put("workload", n(copy.get("workload")) - 1);
          candidates.add(copy);
        }
        require(Rules.recommend(e, candidates).isPresent(), "人员技能、区域或负载不匹配");
        db.assign(id, technicianId);
      }
      case "start" -> {
        require(s.equals("ASSIGNED"), "仅已派单工单可开始");
        db.start(id);
      }
      case "repair" -> {
        require(s.equals("IN_PROGRESS"), "仅维修中工单可完工");
        require(!note.isBlank(), "请填写维修记录");
        db.repair(id, note);
      }
      case "accept" -> {
        require(s.equals("AWAITING_ACCEPTANCE"), "仅待验收工单可验收");
        require(!note.isBlank(), "请填写验收记录");
        db.accept(id, note);
        boolean active =
            db.orders().stream()
                .anyMatch(
                    a ->
                        n(a.get("equipment_id")) == n(w.get("equipment_id"))
                            && !a.get("status").equals("CLOSED"));
        boolean pending =
            db.pending().stream()
                .anyMatch(a -> n(a.get("equipment_id")) == n(w.get("equipment_id")));
        if (!active && !pending) db.equipmentStatus(n(w.get("equipment_id")), "正常");
      }
      case "reject" -> {
        require(s.equals("AWAITING_ACCEPTANCE"), "仅待验收工单可退回");
        require(!note.isBlank(), "请填写退回原因");
        db.reject(id, note);
      }
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知动作");
    }
    db.audit(action.toUpperCase(), "order=" + id + "; " + note);
  }

  static Instant instant(Object o) {
    return o instanceof Timestamp t
        ? t.toInstant()
        : ((java.time.LocalDateTime) o).toInstant(ZoneOffset.UTC);
  }

  public Map<String, Object> stats(Instant from, Instant to) {
    require(to.isAfter(from), "结束时间必须晚于开始时间");
    var orders = db.orders();
    double repairSeconds = 0;
    int repaired = 0;
    for (var w : orders) {
      if (w.get("repaired_at") != null) {
        var end = instant(w.get("repaired_at"));
        if (!end.isBefore(from) && end.isBefore(to)) {
          repairSeconds += Duration.between(instant(w.get("opened_at")), end).toMillis() / 1000.0;
          repaired++;
        }
      }
    }
    double down = 0;
    for (var e : db.equipment()) {
      List<Instant[]> intervals = new ArrayList<>();
      for (var w : orders) {
        if (n(w.get("equipment_id")) != n(e.get("id"))) continue;
        Instant a = instant(w.get("opened_at")),
            b = w.get("accepted_at") == null ? to : instant(w.get("accepted_at"));
        if (a.isBefore(from)) a = from;
        if (b.isAfter(to)) b = to;
        if (b.isAfter(a)) intervals.add(new Instant[] {a, b});
      }
      for (var m : db.pending()) {
        if (n(m.get("equipment_id")) == n(e.get("id"))) {
          Instant a = instant(m.get("created_at"));
          if (a.isBefore(from)) a = from;
          if (to.isAfter(a)) intervals.add(new Instant[] {a, to});
        }
      }
      intervals.sort(Comparator.comparing(a -> a[0]));
      Instant a = null, b = null;
      for (var v : intervals) {
        if (a == null) {
          a = v[0];
          b = v[1];
        } else if (!v[0].isAfter(b)) {
          if (v[1].isAfter(b)) b = v[1];
        } else {
          down += Duration.between(a, b).toMillis() / 1000.0;
          a = v[0];
          b = v[1];
        }
      }
      if (a != null) down += Duration.between(a, b).toMillis() / 1000.0;
    }
    double total = Duration.between(from, to).toMillis() / 1000.0 * db.equipment().size();
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("from", from);
    r.put("to", to);
    r.put("equipmentCount", db.equipment().size());
    r.put("orderCount", orders.size());
    r.put("openOrders", orders.stream().filter(w -> !w.get("status").equals("CLOSED")).count());
    r.put("escalated", orders.stream().filter(w -> w.get("status").equals("ESCALATED")).count());
    r.put("pendingMessages", db.pending().size());
    r.put("repairedSamples", repaired);
    r.put("mttrHours", repaired == 0 ? null : repairSeconds / repaired / 3600);
    r.put("downtimeHours", down / 3600);
    r.put("availabilityPercent", total == 0 ? null : 100 * (1 - down / total));
    return r;
  }
}
