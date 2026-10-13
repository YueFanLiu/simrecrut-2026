package fr.isep.simrecrut;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Load official RuoYi identity, system controllers and business modules.
@SpringBootApplication(scanBasePackages = {"fr.isep.simrecrut", "com.ruoyi.framework", "com.ruoyi.system", "com.ruoyi.common", "com.ruoyi.web.controller", "com.ruoyi.quartz", "com.ruoyi.generator"}, exclude = UserDetailsServiceAutoConfiguration.class)
public class SimRecrutApplication {

    public static void main(String[] args) {
        if (Runtime.version().feature() != 17) {
            throw new IllegalStateException("Run the SimRecrut backend with Java 17.");
        }
        SpringApplication.run(SimRecrutApplication.class, args);
    }
}
