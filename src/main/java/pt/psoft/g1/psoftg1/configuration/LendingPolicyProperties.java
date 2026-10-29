package pt.psoft.g1.psoftg1.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Setup-time externalized configuration properties for Lending Policies.
 * Supports configuring durations, thresholds, renewal limits, and selection criteria
 * via application.properties or environment variables without changing source code
 * (Lending Policies Specification v1.0, Section 5).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "lending.policies")
public class LendingPolicyProperties {

    private PolicyParams standard = new PolicyParams(15, 3, 200, 2, 15);
    private AdaptiveParams adaptive = new AdaptiveParams(5, 1, 2, 30, 15, 7, 2, 15, 200);
    private SelectionParams selection = new SelectionParams(6, 3, 80.0);

    // Additional extensible policy slots
    private PolicyParams longTerm = new PolicyParams(30, 5, 100, 2, 30);
    private PolicyParams shortTerm = new PolicyParams(7, 5, 400, 1, 7);
    private PolicyParams premium = new PolicyParams(30, 5, 100, 3, 30);

    @Data
    public static class PolicyParams {
        private int durationInDays;
        private int maxSimultaneousLendings;
        private int fineValuePerDayInCents;
        private int maxRenewals;
        private int renewalPeriodInDays;

        public PolicyParams() {
            this(15, 3, 200, 2, 15);
        }

        public PolicyParams(int durationInDays, int maxSimultaneousLendings, int fineValuePerDayInCents) {
            this(durationInDays, maxSimultaneousLendings, fineValuePerDayInCents, 2, durationInDays);
        }

        public PolicyParams(int durationInDays, int maxSimultaneousLendings, int fineValuePerDayInCents,
                            int maxRenewals, int renewalPeriodInDays) {
            this.durationInDays = durationInDays;
            this.maxSimultaneousLendings = maxSimultaneousLendings;
            this.fineValuePerDayInCents = fineValuePerDayInCents;
            this.maxRenewals = maxRenewals;
            this.renewalPeriodInDays = renewalPeriodInDays;
        }

        public int getLendingPeriod() {
            return durationInDays;
        }

        public void setLendingPeriod(int lendingPeriod) {
            this.durationInDays = lendingPeriod;
        }

        public int getMaxActiveLendings() {
            return maxSimultaneousLendings;
        }

        public void setMaxActiveLendings(int maxActiveLendings) {
            this.maxSimultaneousLendings = maxActiveLendings;
        }

        public int getRenewalPeriod() {
            return renewalPeriodInDays;
        }

        public void setRenewalPeriod(int renewalPeriod) {
            this.renewalPeriodInDays = renewalPeriod;
        }

        public void validateConstraints() {
            if (maxSimultaneousLendings <= 0) {
                throw new IllegalArgumentException("MaxActiveLendings must be greater than 0");
            }
            if (durationInDays <= 0) {
                throw new IllegalArgumentException("LendingPeriod must be greater than 0");
            }
            if (maxRenewals < 0) {
                throw new IllegalArgumentException("MaxRenewals must be greater than or equal to 0");
            }
            if (renewalPeriodInDays <= 0) {
                throw new IllegalArgumentException("RenewalPeriod must be greater than 0");
            }
        }
    }

    @Data
    public static class AdaptiveParams {
        private int maxActiveLendings;
        private int firstThreshold;
        private int secondThreshold;
        private int longPeriod;
        private int standardPeriod;
        private int shortPeriod;
        private int maxRenewals;
        private int renewalPeriod;
        private int fineValuePerDayInCents;

        public AdaptiveParams() {
            this(5, 1, 2, 30, 15, 7, 2, 15, 200);
        }

        public AdaptiveParams(int maxActiveLendings, int firstThreshold, int secondThreshold,
                              int longPeriod, int standardPeriod, int shortPeriod,
                              int maxRenewals, int renewalPeriod, int fineValuePerDayInCents) {
            this.maxActiveLendings = maxActiveLendings;
            this.firstThreshold = firstThreshold;
            this.secondThreshold = secondThreshold;
            this.longPeriod = longPeriod;
            this.standardPeriod = standardPeriod;
            this.shortPeriod = shortPeriod;
            this.maxRenewals = maxRenewals;
            this.renewalPeriod = renewalPeriod;
            this.fineValuePerDayInCents = fineValuePerDayInCents;
        }

        /**
         * Validates Section 3.5 constraints:
         * - 0 < FirstThreshold < SecondThreshold < MaxActiveLendings
         * - LongPeriod > StandardPeriod > ShortPeriod > 0
         */
        public void validateConstraints() {
            if (firstThreshold <= 0) {
                throw new IllegalArgumentException("FirstThreshold must be greater than 0");
            }
            if (secondThreshold <= firstThreshold) {
                throw new IllegalArgumentException("SecondThreshold must be greater than FirstThreshold");
            }
            if (maxActiveLendings <= secondThreshold) {
                throw new IllegalArgumentException("MaxActiveLendings must be greater than SecondThreshold");
            }
            if (shortPeriod <= 0) {
                throw new IllegalArgumentException("ShortPeriod must be greater than 0");
            }
            if (standardPeriod <= shortPeriod) {
                throw new IllegalArgumentException("StandardPeriod must be greater than ShortPeriod");
            }
            if (longPeriod <= standardPeriod) {
                throw new IllegalArgumentException("LongPeriod must be greater than StandardPeriod");
            }
            if (maxRenewals < 0) {
                throw new IllegalArgumentException("MaxRenewals must be greater than or equal to 0");
            }
            if (renewalPeriod <= 0) {
                throw new IllegalArgumentException("RenewalPeriod must be greater than 0");
            }
        }
    }

    @Data
    public static class SelectionParams {
        private int historyPeriod;
        private int minCompletedLendings;
        private double minOnTimeRatio;

        public SelectionParams() {
            this(6, 3, 80.0);
        }

        public SelectionParams(int historyPeriod, int minCompletedLendings, double minOnTimeRatio) {
            this.historyPeriod = historyPeriod;
            this.minCompletedLendings = minCompletedLendings;
            this.minOnTimeRatio = minOnTimeRatio;
        }

        /**
         * Validates Section 4.2 constraints:
         * - HistoryPeriod > 0
         * - MinCompletedLendings > 0
         * - 0 <= MinOnTimeRatio <= 100
         */
        public void validateConstraints() {
            if (historyPeriod <= 0) {
                throw new IllegalArgumentException("HistoryPeriod must be greater than 0");
            }
            if (minCompletedLendings <= 0) {
                throw new IllegalArgumentException("MinCompletedLendings must be greater than 0");
            }
            if (minOnTimeRatio < 0.0 || minOnTimeRatio > 100.0) {
                throw new IllegalArgumentException("MinOnTimeRatio must be between 0 and 100");
            }
        }
    }
}
