package pt.psoft.g1.psoftg1.lendingmanagement.api;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for modifying lending policy periods/parameters in runtime.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLendingPolicyRequest {

    @Positive(message = "Lending duration must be greater than zero")
    private int durationInDays;

    @Positive(message = "Maximum simultaneous lendings must be greater than zero")
    private Integer maxSimultaneousLendings;

    private Integer fineValuePerDayInCents;
}

