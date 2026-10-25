package pt.psoft.g1.psoftg1.lendingmanagement.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pt.psoft.g1.psoftg1.lendingmanagement.policies.LendingPolicy;

/**
 * View representation of a lending policy for admin inspection and management.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LendingPolicyView {
    private String policyName;
    private String description;
    private int durationInDays;
    private int maxSimultaneousLendings;
    private int fineValuePerDayInCents;
    private int priority;

    public static LendingPolicyView from(LendingPolicy policy) {
        return new LendingPolicyView(
                policy.getPolicyName(),
                policy.getDescription(),
                policy.getLendingDurationInDays(),
                policy.getMaxSimultaneousLendings(),
                policy.getFineValuePerDayInCents(),
                policy.getPriority()
        );
    }
}

