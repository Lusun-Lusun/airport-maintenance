package airport;

import java.util.*;

public class Rules {
  public static String priority(int criticality, boolean flights) {
    if (flights) return "P1";
    return criticality == 1 ? "P2" : criticality == 2 ? "P3" : "P4";
  }

  public static Optional<Map<String, Object>> recommend(
      Map<String, Object> e, List<Map<String, Object>> techs) {
    return techs.stream()
        .filter(t -> Boolean.TRUE.equals(t.get("active")))
        .filter(
            t ->
                Arrays.asList(t.get("skills").toString().split(","))
                    .contains(e.get("skill").toString()))
        .filter(t -> t.get("area").equals(e.get("area")))
        .filter(
            t -> ((Number) t.get("workload")).intValue() < ((Number) t.get("capacity")).intValue())
        .min(
            Comparator.<Map<String, Object>>comparingDouble(
                    t ->
                        ((Number) t.get("workload")).doubleValue()
                            / ((Number) t.get("capacity")).doubleValue())
                .thenComparingLong(t -> ((Number) t.get("id")).longValue()));
  }
}
