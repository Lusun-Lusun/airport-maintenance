package airport;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxWorker {
  final Store db;
  final MaintenanceService service;

  public OutboxWorker(Store db, MaintenanceService service) {
    this.db = db;
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${airport.outbox.delay:3000}")
  public void run() {
    for (var m : db.pending()) {
      long id = ((Number) m.get("message_id")).longValue();
      try {
        service.deliver(id, false);
      } catch (Exception e) {
        db.failed(
            id,
            e.getMessage() == null
                ? e.getClass().getSimpleName()
                : e.getMessage().substring(0, Math.min(e.getMessage().length(), 490)));
      }
    }
  }
}
