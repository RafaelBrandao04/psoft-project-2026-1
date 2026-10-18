package pt.psoft.g1.psoftg1.shared.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ForbiddenNameTest {

    @Test
    void testConstructorAndGetter() {
        ForbiddenName fn = new ForbiddenName("Admin");
        assertThat(fn.getForbiddenName()).isEqualTo("Admin");
    }

    @Test
    void testSetter() {
        ForbiddenName fn = new ForbiddenName();
        fn.setForbiddenName("Root");
        assertThat(fn.getForbiddenName()).isEqualTo("Root");
    }
}
