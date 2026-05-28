package ma.enset.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "finpulse")
@Data
public class FinPulseProperties {

    private Simulation simulation = new Simulation();
    private Nci nci = new Nci();

    @Data
    public static class Simulation {
        private boolean enabled = true;
        private long schedulerInterval = 30000;
    }

    @Data
    public static class Nci {
        private double minValue = 0.0;
        private double maxValue = 1.0;
        private double alertThreshold = 0.3;
    }
}
