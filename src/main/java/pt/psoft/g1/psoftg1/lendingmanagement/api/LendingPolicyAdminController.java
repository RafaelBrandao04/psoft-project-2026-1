package pt.psoft.g1.psoftg1.lendingmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.lendingmanagement.policies.DynamicLendingPolicy;
import pt.psoft.g1.psoftg1.lendingmanagement.policies.LendingPolicy;
import pt.psoft.g1.psoftg1.lendingmanagement.policies.LendingPolicyResolver;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for LMS Administrators to view, edit, create, and delete
 * lending policies dynamically at runtime without requiring application restart.
 */
@Tag(name = "Lending Policies Admin", description = "Admin endpoints for dynamic runtime management of lending policies")
@RestController
@RequestMapping("/api/admin/lending-policies")
@RequiredArgsConstructor
public class LendingPolicyAdminController {

    private final LendingPolicyResolver policyResolver;

    @Operation(summary = "List all active lending policies and their runtime parameters")
    @GetMapping
    public ResponseEntity<List<LendingPolicyView>> getAllPolicies() {
        List<LendingPolicyView> views = policyResolver.getAllPolicies().stream()
                .map(LendingPolicyView::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(views);
    }

    @Operation(summary = "Get details of a specific lending policy by name")
    @GetMapping("/{policyName}")
    public ResponseEntity<LendingPolicyView> getPolicyByName(@PathVariable String policyName) {
        LendingPolicy policy = policyResolver.getPolicyByName(policyName)
                .orElseThrow(() -> new NotFoundException("Policy not found with name: " + policyName));
        return ResponseEntity.ok(LendingPolicyView.from(policy));
    }

    @Operation(summary = "Edit policy duration, max books or fine in runtime")
    @PutMapping("/{policyName}")
    public ResponseEntity<LendingPolicyView> updatePolicy(@PathVariable String policyName,
                                                         @Valid @RequestBody UpdateLendingPolicyRequest request) {
        LendingPolicy updated = policyResolver.updatePolicy(
                policyName,
                request.getDurationInDays(),
                request.getMaxSimultaneousLendings(),
                request.getFineValuePerDayInCents()
        );
        return ResponseEntity.ok(LendingPolicyView.from(updated));
    }

    @Operation(summary = "Create and register a new lending policy in runtime")
    @PostMapping
    public ResponseEntity<LendingPolicyView> createPolicy(@Valid @RequestBody CreateDynamicPolicyRequest request) {
        DynamicLendingPolicy newPolicy = new DynamicLendingPolicy(
                request.getPolicyName(),
                request.getDescription(),
                request.getDurationInDays(),
                request.getMaxSimultaneousLendings(),
                request.getFineValuePerDayInCents(),
                request.getPriority(),
                request.getMinActiveLendings(),
                request.getMaxActiveLendings(),
                request.getReaderTypeKeyword(),
                request.getBookGenre()
        );
        policyResolver.registerPolicy(newPolicy);
        return ResponseEntity.status(HttpStatus.CREATED).body(LendingPolicyView.from(newPolicy));
    }

    @Operation(summary = "Remove a dynamic lending policy at runtime")
    @DeleteMapping("/{policyName}")
    public ResponseEntity<Void> deletePolicy(@PathVariable String policyName) {
        boolean removed = policyResolver.removePolicy(policyName);
        if (!removed) {
            throw new NotFoundException("Policy not found with name: " + policyName);
        }
        return ResponseEntity.noContent().build();
    }
}

