package airport;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {
  public static void main(String[] args) {
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    SpringApplication.run(Application.class, args);
  }
}
