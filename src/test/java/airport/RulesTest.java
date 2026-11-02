package airport;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

class RulesTest {
  static Map<String, Object> tech(
      long id, String skills, String area, int load, int capacity, boolean active) {
    return Map.of(
        "id",
        id,
        "skills",
        skills,
        "area",
        area,
        "workload",
        load,
        "capacity",
        capacity,
        "active",
        active);
  }

  final Map<String, Object> equipment = Map.of("skill", "电气", "area", "T1");

  @ParameterizedTest
  @CsvSource({"1,true,P1", "2,true,P1", "3,true,P1", "1,false,P2", "2,false,P3", "3,false,P4"})
  void priorityMatrix(int c, boolean f, String expected) {
    assertEquals(expected, Rules.priority(c, f));
  }

  @Test
  void excludesWrongSkillAreaInactiveAndFull() {
    var candidates =
        List.of(
            tech(1, "机械", "T1", 0, 3, true),
            tech(2, "电气", "T2", 0, 3, true),
            tech(3, "电气", "T1", 3, 3, true),
            tech(4, "电气", "T1", 0, 3, false));
    assertTrue(Rules.recommend(equipment, candidates).isEmpty());
  }

  @Test
  void choosesUtilizationRatherThanRawCount() {
    var a = tech(1, "电气", "T1", 1, 2, true);
    var b = tech(2, "机械,电气", "T1", 2, 8, true);
    assertEquals(2L, Rules.recommend(equipment, List.of(a, b)).orElseThrow().get("id"));
  }

  @Test
  void deterministicTieBreak() {
    assertEquals(
        1L,
        Rules.recommend(
                equipment,
                List.of(tech(2, "电气", "T1", 0, 3, true), tech(1, "电气", "T1", 0, 3, true)))
            .orElseThrow()
            .get("id"));
  }

  @Test
  void zeroCapacityExcluded() {
    assertTrue(Rules.recommend(equipment, List.of(tech(1, "电气", "T1", 0, 0, true))).isEmpty());
  }

  @Test
  void tagsRequireExactMatch() {
    assertTrue(
        Rules.recommend(
                Map.of("skill", "电", "area", "T1"), List.of(tech(1, "电气", "T1", 0, 3, true)))
            .isEmpty());
  }

  @Test
  void emptyCandidatesEscalate() {
    assertTrue(Rules.recommend(equipment, List.of()).isEmpty());
  }
}
