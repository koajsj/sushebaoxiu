package com.campus.repair.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="app.sla")
@lombok.Getter
@lombok.Setter
public class SlaProperties {
    private Threshold low = new Threshold(Duration.ofHours(24),Duration.ofHours(24),Duration.ofHours(72));
    private Threshold normal = new Threshold(Duration.ofHours(8),Duration.ofHours(8),Duration.ofHours(24));
    private Threshold high = new Threshold(Duration.ofHours(2),Duration.ofHours(2),Duration.ofHours(8));
    public Threshold forPriority(String priority) {
        return switch(priority) { case "LOW" -> low; case "HIGH" -> high; default -> normal; };
    }
    @lombok.Getter @lombok.Setter
    public static class Threshold {
        private Duration response;
        private Duration start;
        private Duration repair;
        public Threshold() { this(Duration.ofHours(8),Duration.ofHours(8),Duration.ofHours(24)); }
        public Threshold(Duration response,Duration start,Duration repair) { this.response=response;this.start=start;this.repair=repair; }
        public void setResponse(Duration value) { response=positive(value); }
        public void setStart(Duration value) { start=positive(value); }
        public void setRepair(Duration value) { repair=positive(value); }
        private static Duration positive(Duration value) {
            if(value==null||value.compareTo(Duration.ofSeconds(1))<0) throw new IllegalArgumentException("SLA duration must be at least one second");
            return value;
        }
    }
}
