package pt.psoft.g1.psoftg1.lendingmanagement.policies;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.configuration.LendingPolicyProperties;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Resolves the applicable {@link LendingPolicy} for a given lending or renewal request,
 * implementing Section 4 (Lending Policy Selection) of Lending Policies Specification v1.0,
 * while also supporting dynamic runtime policy registration and administration.
 *
 * <p>Section 4 Rules:
 * <ul>
 *   <li>4.1. Standard Lending Policy applies by default.</li>
 *   <li>4.2. Adaptive Lending Policy applies when, during the previous {@code HistoryPeriod (> 0)} months,
 *            the reader:
 *       <ul>
 *         <li>4.2.1. has completed at least {@code MinCompletedLendings (> 0)} lendings;</li>
 *         <li>4.2.2. has returned at least {@code MinOnTimeRatio (0-100%)} of those lendings on time; and</li>
 *         <li>4.2.3. has no currently overdue lending.</li>
 *       </ul>
 *   </li>
 *   <li>4.3. The applicable lending policy must be re-evaluated whenever a new lending or renewal is requested.</li>
 * </ul>
 */
@Component
public class LendingPolicyResolver {

    private final List<LendingPolicy> policies = new CopyOnWriteArrayList<>();

    private int historyPeriodMonths = 6;
    private int minCompletedLendings = 3;
    private double minOnTimeRatio = 80.0;

    @Autowired
    public LendingPolicyResolver(List<LendingPolicy> initialPolicies, LendingPolicyProperties properties) {
        if (initialPolicies != null) {
            this.policies.addAll(initialPolicies);
        }
        if (properties != null && properties.getSelection() != null) {
            properties.getSelection().validateConstraints();
            this.historyPeriodMonths = properties.getSelection().getHistoryPeriod();
            this.minCompletedLendings = properties.getSelection().getMinCompletedLendings();
            this.minOnTimeRatio = properties.getSelection().getMinOnTimeRatio();
        }
    }

    public LendingPolicyResolver(List<LendingPolicy> initialPolicies) {
        this(initialPolicies, null);
    }

    public LendingPolicyResolver(StandardLendingPolicy standardPolicy,
                                 AdaptiveLendingPolicy adaptivePolicy,
                                 int historyPeriodMonths,
                                 int minCompletedLendings,
                                 double minOnTimeRatio) {
        validateSelectionConstraints(historyPeriodMonths, minCompletedLendings, minOnTimeRatio);
        this.historyPeriodMonths = historyPeriodMonths;
        this.minCompletedLendings = minCompletedLendings;
        this.minOnTimeRatio = minOnTimeRatio;
        if (standardPolicy != null) {
            this.policies.add(standardPolicy);
        }
        if (adaptivePolicy != null) {
            adaptivePolicy.setHistoryPeriodMonths(historyPeriodMonths);
            adaptivePolicy.setMinCompletedLendings(minCompletedLendings);
            adaptivePolicy.setMinOnTimeRatio(minOnTimeRatio);
            this.policies.add(adaptivePolicy);
        }
    }

    public static void validateSelectionConstraints(int historyPeriodMonths, int minCompletedLendings, double minOnTimeRatio) {
        if (historyPeriodMonths <= 0) {
            throw new IllegalArgumentException("HistoryPeriod must be greater than 0");
        }
        if (minCompletedLendings <= 0) {
            throw new IllegalArgumentException("MinCompletedLendings must be greater than 0");
        }
        if (minOnTimeRatio < 0.0 || minOnTimeRatio > 100.0) {
            throw new IllegalArgumentException("MinOnTimeRatio must be between 0 and 100");
        }
    }

    /**
     * Evaluates whether a reader qualifies for the Adaptive Lending Policy based on Section 4.2:
     * <ul>
     *   <li>4.2.1. Completed at least {@code MinCompletedLendings (> 0)} lendings during the previous {@code HistoryPeriod (> 0)} months;</li>
     *   <li>4.2.2. Returned at least {@code MinOnTimeRatio (0-100%)} of those lendings on time;</li>
     *   <li>4.2.3. Has no currently overdue lending.</li>
     * </ul>
     */
    public boolean qualifiesForAdaptivePolicy(List<Lending> activeLendings, List<Lending> readerHistory) {
        return qualifiesForAdaptivePolicy(activeLendings, readerHistory, LocalDate.now());
    }

    public boolean qualifiesForAdaptivePolicy(List<Lending> activeLendings, List<Lending> readerHistory, LocalDate referenceDate) {
        // 4.2.3. Has no currently overdue lending
        if (activeLendings != null) {
            for (Lending active : activeLendings) {
                if (active.getDaysDelayed() > 0) {
                    return false;
                }
            }
        }

        if (readerHistory == null || readerHistory.isEmpty()) {
            return false;
        }

        LocalDate cutoffDate = referenceDate.minusMonths(historyPeriodMonths);
        int completedInPeriod = 0;
        int onTimeInPeriod = 0;

        for (Lending l : readerHistory) {
            // If an unreturned lending exists in the history list and is overdue, rule 4.2.3 also fails
            if (l.getReturnedDate() == null) {
                if (l.getDaysDelayed() > 0) {
                    return false;
                }
                continue;
            }

            // Completed lending within the previous HistoryPeriod months
            LocalDate completionDate = l.getReturnedDate();
            LocalDate start = l.getStartDate() != null ? l.getStartDate() : completionDate;
            if (!completionDate.isBefore(cutoffDate) && !start.isBefore(cutoffDate)) {
                completedInPeriod++;
                if (l.getDaysDelayed() == 0) {
                    onTimeInPeriod++;
                }
            }
        }

        // 4.2.1. Has completed at least MinCompletedLendings lendings
        if (completedInPeriod < minCompletedLendings) {
            return false;
        }

        // 4.2.2. Has returned at least MinOnTimeRatio (0-100%) of those lendings on time
        double onTimePercentage = (onTimeInPeriod * 100.0) / completedInPeriod;
        return onTimePercentage >= minOnTimeRatio;
    }

    /**
     * Resolves the applicable lending policy according to Section 4 of Lending Policies Specification v1.0:
     * <ul>
     *   <li>4.1. Standard Lending Policy applies by default.</li>
     *   <li>4.2. Adaptive Lending Policy applies when the reader satisfies 4.2.1, 4.2.2, and 4.2.3.</li>
     *   <li>4.3. Re-evaluated whenever a new lending or renewal is requested.</li>
     * </ul>
     * Also supports higher-priority dynamic policies registered at runtime by an administrator.
     */
    public LendingPolicy resolvePolicy(ReaderDetails reader, Book book, List<Lending> activeLendings, List<Lending> readerHistory) {
        // Check if any custom/dynamic policy with priority higher than Adaptive (e.g. > 25) is registered and applicable
        Optional<LendingPolicy> dynamicOverride = policies.stream()
                .filter(p -> p instanceof DynamicLendingPolicy)
                .sorted(Comparator.comparingInt(LendingPolicy::getPriority).reversed())
                .filter(p -> p.isApplicable(reader, book, activeLendings, readerHistory))
                .findFirst();
        if (dynamicOverride.isPresent()) {
            return dynamicOverride.get();
        }

        // Section 4.2: Check if Adaptive Lending Policy applies
        Optional<LendingPolicy> adaptivePolicy = getPolicyByName(AdaptiveLendingPolicy.POLICY_NAME);
        if (adaptivePolicy.isPresent() && qualifiesForAdaptivePolicy(activeLendings, readerHistory)) {
            return adaptivePolicy.get();
        }

        // Section 4.1: Standard Lending Policy applies by default
        Optional<LendingPolicy> standardPolicy = getPolicyByName(StandardLendingPolicy.POLICY_NAME);
        if (standardPolicy.isPresent()) {
            return standardPolicy.get();
        }

        return new StandardLendingPolicy();
    }

    /**
     * Resolves the highest-priority applicable lending policy when no reader history is provided
     * (used by legacy/direct policy resolution or when evaluating activeLendings-only rules).
     */
    public LendingPolicy resolvePolicy(ReaderDetails reader, Book book, List<Lending> activeLendings) {
        // If AdaptiveLendingPolicy is the only non-standard policy registered (Specification v1.0 mode without history),
        // default to StandardLendingPolicy per Section 4.1.
        boolean hasLegacyTierPolicies = policies.stream()
                .anyMatch(p -> p instanceof LongTermLendingPolicy
                        || p instanceof ShortTermLendingPolicy
                        || p instanceof PremiumReaderLendingPolicy
                        || p instanceof DynamicLendingPolicy);

        if (!hasLegacyTierPolicies) {
            return resolvePolicy(reader, book, activeLendings, List.of());
        }

        return policies.stream()
                .sorted(Comparator.comparingInt(LendingPolicy::getPriority).reversed())
                .filter(policy -> policy.isApplicable(reader, book, activeLendings))
                .findFirst()
                .orElseGet(StandardLendingPolicy::new);
    }

    public LendingPolicy resolvePolicy(ReaderDetails reader, Book book) {
        return resolvePolicy(reader, book, null);
    }

    public List<LendingPolicy> getAllPolicies() {
        List<LendingPolicy> sorted = new ArrayList<>(policies);
        sorted.sort(Comparator.comparingInt(LendingPolicy::getPriority).reversed());
        return sorted;
    }

    public Optional<LendingPolicy> getPolicyByName(String policyName) {
        if (policyName == null) {
            return Optional.empty();
        }
        return policies.stream()
                .filter(p -> p.getPolicyName().equalsIgnoreCase(policyName.trim()))
                .findFirst();
    }

    /**
     * Updates an existing policy's parameters in runtime (no restart required).
     */
    public LendingPolicy updatePolicy(String policyName, Integer durationInDays, Integer maxSimultaneousLendings, Integer fineValuePerDayInCents) {
        LendingPolicy policy = getPolicyByName(policyName)
                .orElseThrow(() -> new NotFoundException("Lending policy not found: " + policyName));

        if (durationInDays != null && durationInDays > 0) {
            policy.setLendingDurationInDays(durationInDays);
        }
        if (maxSimultaneousLendings != null && maxSimultaneousLendings > 0) {
            policy.setMaxSimultaneousLendings(maxSimultaneousLendings);
        }
        if (fineValuePerDayInCents != null && fineValuePerDayInCents >= 0) {
            policy.setFineValuePerDayInCents(fineValuePerDayInCents);
        }
        return policy;
    }

    /**
     * Registers a new dynamic lending policy at runtime or replaces one with the same name.
     */
    public LendingPolicy registerPolicy(LendingPolicy newPolicy) {
        policies.removeIf(p -> p.getPolicyName().equalsIgnoreCase(newPolicy.getPolicyName()));
        policies.add(newPolicy);
        return newPolicy;
    }

    /**
     * Removes a lending policy by name at runtime.
     */
    public boolean removePolicy(String policyName) {
        if (policyName == null) {
            return false;
        }
        return policies.removeIf(p -> p.getPolicyName().equalsIgnoreCase(policyName.trim()));
    }

    public int getHistoryPeriodMonths() {
        return historyPeriodMonths;
    }

    public void setHistoryPeriodMonths(int historyPeriodMonths) {
        if (historyPeriodMonths <= 0) {
            throw new IllegalArgumentException("HistoryPeriod must be greater than 0");
        }
        this.historyPeriodMonths = historyPeriodMonths;
        getPolicyByName(AdaptiveLendingPolicy.POLICY_NAME)
                .filter(AdaptiveLendingPolicy.class::isInstance)
                .map(AdaptiveLendingPolicy.class::cast)
                .ifPresent(a -> a.setHistoryPeriodMonths(historyPeriodMonths));
    }

    public int getMinCompletedLendings() {
        return minCompletedLendings;
    }

    public void setMinCompletedLendings(int minCompletedLendings) {
        if (minCompletedLendings <= 0) {
            throw new IllegalArgumentException("MinCompletedLendings must be greater than 0");
        }
        this.minCompletedLendings = minCompletedLendings;
        getPolicyByName(AdaptiveLendingPolicy.POLICY_NAME)
                .filter(AdaptiveLendingPolicy.class::isInstance)
                .map(AdaptiveLendingPolicy.class::cast)
                .ifPresent(a -> a.setMinCompletedLendings(minCompletedLendings));
    }

    public double getMinOnTimeRatio() {
        return minOnTimeRatio;
    }

    public void setMinOnTimeRatio(double minOnTimeRatio) {
        if (minOnTimeRatio < 0.0 || minOnTimeRatio > 100.0) {
            throw new IllegalArgumentException("MinOnTimeRatio must be between 0 and 100");
        }
        this.minOnTimeRatio = minOnTimeRatio;
        getPolicyByName(AdaptiveLendingPolicy.POLICY_NAME)
                .filter(AdaptiveLendingPolicy.class::isInstance)
                .map(AdaptiveLendingPolicy.class::cast)
                .ifPresent(a -> a.setMinOnTimeRatio(minOnTimeRatio));
    }
}
