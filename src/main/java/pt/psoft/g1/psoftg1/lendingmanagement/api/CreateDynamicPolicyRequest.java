package pt.psoft.g1.psoftg1.lendingmanagement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating and registering a new lending policy at runtime.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDynamicPolicyRequest {

    @NotBlank(message = "Policy name is required")
    private String policyName;

    private String description;

    @Positive(message = "Lending duration must be greater than zero")
    private int durationInDays;

    @Positive(message = "Maximum simultaneous lendings must be greater than zero")
    private int maxSimultaneousLendings;

    private int fineValuePerDayInCents;

    private int priority;

    private Integer minActiveLendings;
    private Integer maxActiveLendings;
    private String readerTypeKeyword;
    private String bookGenre;
}

