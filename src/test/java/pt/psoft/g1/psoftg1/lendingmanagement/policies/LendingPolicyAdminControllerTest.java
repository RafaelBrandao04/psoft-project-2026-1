package pt.psoft.g1.psoftg1.lendingmanagement.policies;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pt.psoft.g1.psoftg1.configuration.LendingPolicyProperties;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.lendingmanagement.api.CreateDynamicPolicyRequest;
import pt.psoft.g1.psoftg1.lendingmanagement.api.LendingPolicyAdminController;
import pt.psoft.g1.psoftg1.lendingmanagement.api.LendingPolicyView;
import pt.psoft.g1.psoftg1.lendingmanagement.api.UpdateLendingPolicyRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LendingPolicyAdminControllerTest {

    private LendingPolicyResolver resolver;
    private LendingPolicyAdminController controller;

    @BeforeEach
    void setUp() {
        LendingPolicyProperties properties = new LendingPolicyProperties();
        StandardLendingPolicy standardPolicy = new StandardLendingPolicy(properties);
        LongTermLendingPolicy longTermPolicy = new LongTermLendingPolicy(properties);
        ShortTermLendingPolicy shortTermPolicy = new ShortTermLendingPolicy(properties);

        resolver = new LendingPolicyResolver(List.of(standardPolicy, longTermPolicy, shortTermPolicy));
        controller = new LendingPolicyAdminController(resolver);
    }

    @Test
    @DisplayName("GET /api/admin/lending-policies lists all policies")
    void testGetAllPolicies() {
        ResponseEntity<List<LendingPolicyView>> response = controller.getAllPolicies();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
    }

    @Test
    @DisplayName("GET /api/admin/lending-policies/{name} returns policy view or throws NotFoundException")
    void testGetPolicyByName() {
        ResponseEntity<LendingPolicyView> response = controller.getPolicyByName("LONG_TERM");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(30, response.getBody().getDurationInDays());

        assertThrows(NotFoundException.class, () -> controller.getPolicyByName("NON_EXISTING"));
    }

    @Test
    @DisplayName("PUT /api/admin/lending-policies/{name} updates duration, max books and fine in runtime")
    void testUpdatePolicyInRuntime() {
        UpdateLendingPolicyRequest req = new UpdateLendingPolicyRequest(45, 6, 150);
        ResponseEntity<LendingPolicyView> response = controller.updatePolicy("LONG_TERM", req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(45, response.getBody().getDurationInDays());
        assertEquals(6, response.getBody().getMaxSimultaneousLendings());
        assertEquals(150, response.getBody().getFineValuePerDayInCents());
    }

    @Test
    @DisplayName("POST /api/admin/lending-policies creates and registers a dynamic policy in runtime")
    void testCreateDynamicPolicyInRuntime() {
        CreateDynamicPolicyRequest req = new CreateDynamicPolicyRequest(
                "CUSTOM_WEEKEND",
                "Weekend lending policy",
                4,
                2,
                300,
                40,
                0,
                2,
                null,
                null
        );

        ResponseEntity<LendingPolicyView> response = controller.createPolicy(req);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CUSTOM_WEEKEND", response.getBody().getPolicyName());
        assertEquals(4, response.getBody().getDurationInDays());

        // Now policy exists in resolver
        assertTrue(resolver.getPolicyByName("CUSTOM_WEEKEND").isPresent());
    }

    @Test
    @DisplayName("DELETE /api/admin/lending-policies/{name} removes a dynamic policy")
    void testDeletePolicy() {
        CreateDynamicPolicyRequest req = new CreateDynamicPolicyRequest(
                "TEMP_POLICY", "Temp", 5, 2, 100, 10, null, null, null, null
        );
        controller.createPolicy(req);

        ResponseEntity<Void> response = controller.deletePolicy("TEMP_POLICY");
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertFalse(resolver.getPolicyByName("TEMP_POLICY").isPresent());
    }
}

