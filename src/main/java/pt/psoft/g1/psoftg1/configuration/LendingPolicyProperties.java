package pt.psoft.g1.psoftg1.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration properties for lending policies.
 * Enables setup-time configuration through application.properties or library.properties
 * without requiring code recompilation (satisfies ASR02 and TC02).
 */
@Component
@ConfigurationProperties(prefix = "lending.policies")
@Data
public class LendingPolicyProperties {

    /**
     * Long-term lending policy (e.g. for readers with <= 1 active book, renting 1st or 2nd book).
     */
    private PolicyConfig longTerm = new PolicyConfig(30, 5, 100);

    /**
     * Standard lending policy (baseline / 3rd book).
     */
    private PolicyConfig standard = new PolicyConfig(15, 5, 200);

    /**
     * Short-term lending policy (e.g. for readers with >= 3 active books, renting 4th+ book, or reference books).
     */
    private PolicyConfig shortTerm = new PolicyConfig(7, 5, 400);

    /**
     * Premium reader lending policy.
     */
    private PolicyConfig premium = new PolicyConfig(30, 5, 100);

    @Data
    public static class PolicyConfig {
        private int durationInDays;
        private int maxSimultaneousLendings;
        private int fineValuePerDayInCents;

        public PolicyConfig() {}

        public PolicyConfig(int durationInDays, int maxSimultaneousLendings, int fineValuePerDayInCents) {
            this.durationInDays = durationInDays;
            this.maxSimultaneousLendings = maxSimultaneousLendings;
            this.fineValuePerDayInCents = fineValuePerDayInCents;
        }
    }
}
