package pt.psoft.g1.psoftg1.lendingmanagement.policies;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Resolves the applicable lending policy dynamically at runtime.
 * Selects the policy with the highest priority that matches the reader, book, and active lendings context.
 *
 * Supports runtime registration of new dynamic policies and runtime parameter updates by LMS administrators.
 */
@Component
public class LendingPolicyResolver {

    private final List<LendingPolicy> policies = new CopyOnWriteArrayList<>();

    @Autowired
    public LendingPolicyResolver(List<LendingPolicy> initialPolicies) {
        if (initialPolicies != null) {
            this.policies.addAll(initialPolicies);
        }
    }

    public LendingPolicyResolver() {
    }

    /**
     * Resolves the policy with highest priority considering reader, book, and active lendings.
     */
    public LendingPolicy resolvePolicy(ReaderDetails reader, Book book, List<Lending> activeLendings) {
        return policies.stream()
                .filter(p -> p.isApplicable(reader, book, activeLendings))
                .max(Comparator.comparingInt(LendingPolicy::getPriority))
                .orElseGet(this::getFallbackPolicy);
    }

    /**
     * Overload for resolution without active lendings list.
     */
    public LendingPolicy resolvePolicy(ReaderDetails reader, Book book) {
        return resolvePolicy(reader, book, Collections.emptyList());
    }

    /**
     * Returns an unmodifiable view of all registered policies.
     */
    public List<LendingPolicy> getAllPolicies() {
        return Collections.unmodifiableList(policies);
    }

    /**
     * Finds a policy by name (case-insensitive).
     */
    public Optional<LendingPolicy> getPolicyByName(String policyName) {
        if (policyName == null) {
            return Optional.empty();
        }
        return policies.stream()
                .filter(p -> p.getPolicyName().equalsIgnoreCase(policyName))
                .findFirst();
    }

    /**
     * Registers a new policy or replaces an existing policy with the same name at runtime.
     */
    public void registerPolicy(LendingPolicy policy) {
        Objects.requireNonNull(policy, "Policy cannot be null");
        // Remove existing policy with same name if present
        policies.removeIf(p -> p.getPolicyName().equalsIgnoreCase(policy.getPolicyName()));
        policies.add(policy);
    }

    /**
     * Removes a policy at runtime by name. The standard policy cannot be removed.
     */
    public boolean removePolicy(String policyName) {
        if (StandardLendingPolicy.POLICY_NAME.equalsIgnoreCase(policyName)) {
            throw new IllegalArgumentException("Standard lending policy cannot be removed");
        }
        return policies.removeIf(p -> p.getPolicyName().equalsIgnoreCase(policyName));
    }

    /**
     * Updates an existing policy's parameters at runtime.
     */
    public LendingPolicy updatePolicy(String policyName, int durationDays, Integer maxBooks, Integer fineCents) {
        LendingPolicy policy = getPolicyByName(policyName)
                .orElseThrow(() -> new NotFoundException("Policy not found with name: " + policyName));

        if (durationDays > 0) {
            policy.setLendingDurationInDays(durationDays);
        }
        if (maxBooks != null && maxBooks > 0) {
            policy.setMaxSimultaneousLendings(maxBooks);
        }
        if (fineCents != null && fineCents >= 0) {
            policy.setFineValuePerDayInCents(fineCents);
        }
        return policy;
    }

    private LendingPolicy getFallbackPolicy() {
        return policies.stream()
                .filter(p -> StandardLendingPolicy.POLICY_NAME.equalsIgnoreCase(p.getPolicyName()))
                .findFirst()
                .orElseGet(StandardLendingPolicy::new);
    }
}
