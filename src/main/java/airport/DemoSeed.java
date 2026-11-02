package airport;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

@Component
public class DemoSeed implements CommandLineRunner {
  final JdbcTemplate jdbc;

  public DemoSeed(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void run(String... args) throws Exception {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class) == 0) {
      try (var c = jdbc.getDataSource().getConnection()) {
        ScriptUtils.executeSqlScript(c, new org.springframework.core.io.support.EncodedResource(new ClassPathResource("demo.sql"), java.nio.charset.StandardCharsets.UTF_8));
      }
    }
  }
}
