package airport;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface Store {
  @Select("SELECT id FROM technician ORDER BY id FOR UPDATE")
  List<Long> lockDispatch();

  @Select("SELECT id FROM work_order WHERE id=#{id} FOR UPDATE")
  Long lockOrder(long id);

  @Select("SELECT * FROM equipment ORDER BY id")
  List<Map<String, Object>> equipment();

  @Select("SELECT * FROM equipment WHERE id=#{id}")
  Map<String, Object> equipmentOne(long id);

  @Select(
      "SELECT t.*, (SELECT COUNT(*) FROM work_order w WHERE w.technician_id=t.id AND w.status <>"
          + " 'CLOSED') AS workload FROM technician t ORDER BY t.id")
  List<Map<String, Object>> technicians();

  @Select(
      "SELECT w.*,e.name AS equipment_name,e.code,e.area,t.name AS technician_name FROM work_order"
          + " w JOIN equipment e ON e.id=w.equipment_id LEFT JOIN technician t ON"
          + " t.id=w.technician_id ORDER BY w.id DESC")
  List<Map<String, Object>> orders();

  @Select("SELECT * FROM work_order WHERE id=#{id}")
  Map<String, Object> order(long id);

  @Select(
      "SELECT i.*,e.name AS equipment_name FROM inspection i JOIN equipment e ON"
          + " e.id=i.equipment_id ORDER BY i.id DESC")
  List<Map<String, Object>> inspections();

  @Select("SELECT * FROM inspection WHERE request_key=#{key}")
  Map<String, Object> inspectionByKey(String key);

  @Insert(
      "INSERT INTO inspection(request_key,equipment_id,abnormal,note,created_at)"
          + " VALUES(#{key},#{equipmentId},#{abnormal},#{note},CURRENT_TIMESTAMP)")
  void inspection(Map<String, Object> p);

  @Insert(
      "INSERT INTO outbox(inspection_id,state,created_at)"
          + " VALUES(#{id},'PENDING',CURRENT_TIMESTAMP)")
  void enqueue(long id);

  @Select(
      "SELECT o.id AS message_id,o.attempts,i.* FROM outbox o JOIN inspection i ON"
          + " i.id=o.inspection_id WHERE o.state='PENDING' ORDER BY o.id")
  List<Map<String, Object>> pending();

  @Select("SELECT * FROM outbox ORDER BY id DESC")
  List<Map<String, Object>> messages();

  @Insert(
      "INSERT INTO work_order(inspection_id,equipment_id,priority,status,technician_id,opened_at)"
          + " VALUES(#{inspectionId},#{equipmentId},#{priority},#{status},#{technicianId},#{openedAt})")
  void createOrder(Map<String, Object> p);

  @Select("SELECT * FROM work_order WHERE inspection_id=#{id}")
  Map<String, Object> byInspection(long id);

  @Update("UPDATE outbox SET state='DONE',attempts=attempts+1,last_error=NULL WHERE id=#{id}")
  void done(long id);

  @Update("UPDATE outbox SET attempts=attempts+1,last_error=#{error} WHERE id=#{id}")
  void failed(@Param("id") long id, @Param("error") String error);

  @Update("UPDATE equipment SET status=#{status} WHERE id=#{id}")
  void equipmentStatus(@Param("id") long id, @Param("status") String status);

  @Insert(
      "INSERT INTO notification(order_id,recipient,message,created_at)"
          + " VALUES(#{id},'维修主管',#{message},CURRENT_TIMESTAMP)")
  void notify(@Param("id") long id, @Param("message") String message);

  @Select("SELECT * FROM notification ORDER BY id DESC")
  List<Map<String, Object>> notifications();

  @Update("UPDATE notification SET is_read=TRUE WHERE id=#{id}")
  int read(long id);

  @Update("UPDATE work_order SET technician_id=#{tech},status='ASSIGNED' WHERE id=#{id}")
  void assign(@Param("id") long id, @Param("tech") long tech);

  @Update("UPDATE work_order SET status='IN_PROGRESS' WHERE id=#{id}")
  void start(long id);

  @Update(
      "UPDATE work_order SET"
          + " status='AWAITING_ACCEPTANCE',repaired_at=CURRENT_TIMESTAMP,repair_note=#{note} WHERE"
          + " id=#{id}")
  void repair(@Param("id") long id, @Param("note") String note);

  @Update(
      "UPDATE work_order SET status='CLOSED',accepted_at=CURRENT_TIMESTAMP,acceptance_note=#{note}"
          + " WHERE id=#{id}")
  void accept(@Param("id") long id, @Param("note") String note);

  @Update(
      "UPDATE work_order SET status='IN_PROGRESS',repaired_at=NULL,acceptance_note=#{note} WHERE"
          + " id=#{id}")
  void reject(@Param("id") long id, @Param("note") String note);

  @Insert(
      "INSERT INTO equipment(code,name,system_name,area,skill,criticality,affects_flights,status)"
          + " VALUES(#{code},#{name},#{system},#{area},#{skill},#{criticality},#{affectsFlights},'正常')")
  void addEquipment(Map<String, Object> p);

  @Insert(
      "INSERT INTO audit_log(action,detail,created_at)"
          + " VALUES(#{action},#{detail},CURRENT_TIMESTAMP)")
  void audit(@Param("action") String action, @Param("detail") String detail);

  @Select("SELECT * FROM audit_log ORDER BY id DESC LIMIT 200")
  List<Map<String, Object>> auditLogs();
}
