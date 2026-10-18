package pt.psoft.g1.psoftg1.usermanagement.model;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void testCreateUser_Constructor_Success() {
        User user = new User("test@example.com", "Password123!");

        assertThat(user.getUsername()).isEqualTo("test@example.com");
        assertThat(user.getPassword()).isNotNull();
        assertTrue(new BCryptPasswordEncoder().matches("Password123!", user.getPassword()));
        assertTrue(user.isEnabled());
        assertTrue(user.isAccountNonExpired());
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isCredentialsNonExpired());
    }

    @Test
    void testCreateUser_InvalidPassword_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new User("test@example.com", "weak"));
    }

    @Test
    void testNewUserFactory_WithoutRole() {
        User user = User.newUser("test@example.com", "Password123!", "John Doe");

        assertThat(user.getUsername()).isEqualTo("test@example.com");
        assertThat(user.getName().getName()).isEqualTo("John Doe");
        assertThat(user.getAuthorities()).isEmpty();
    }

    @Test
    void testNewUserFactory_WithRole() {
        User user = User.newUser("test@example.com", "Password123!", "Jane Doe", Role.LIBRARIAN);

        assertThat(user.getUsername()).isEqualTo("test@example.com");
        assertThat(user.getName().getName()).isEqualTo("Jane Doe");
        assertThat(user.getAuthorities()).hasSize(1);
        assertThat(user.getAuthorities().iterator().next().getAuthority()).isEqualTo(Role.LIBRARIAN);
    }

    @Test
    void testReaderFactory() {
        Reader reader = Reader.newReader("reader@example.com", "Password123!", "Reader Doe");

        assertThat(reader.getUsername()).isEqualTo("reader@example.com");
        assertThat(reader.getName().getName()).isEqualTo("Reader Doe");
        assertThat(reader.getAuthorities()).hasSize(1);
        assertThat(reader.getAuthorities().iterator().next().getAuthority()).isEqualTo(Role.READER);
    }

    @Test
    void testLibrarianFactory() {
        Librarian librarian = Librarian.newLibrarian("lib@example.com", "Password123!", "Librarian Doe");

        assertThat(librarian.getUsername()).isEqualTo("lib@example.com");
        assertThat(librarian.getName().getName()).isEqualTo("Librarian Doe");
        assertThat(librarian.getAuthorities()).hasSize(1);
        assertThat(librarian.getAuthorities().iterator().next().getAuthority()).isEqualTo(Role.LIBRARIAN);
    }

    @Test
    void testSetEnabled_AffectsSecurityFlags() {
        User user = new User("test@example.com", "Password123!");
        assertTrue(user.isEnabled());

        user.setEnabled(false);
        assertFalse(user.isEnabled());
        assertFalse(user.isAccountNonExpired());
        assertFalse(user.isAccountNonLocked());
        assertFalse(user.isCredentialsNonExpired());
    }

    @Test
    void testRoleEntity() {
        Role r1 = new Role(Role.ADMIN);
        Role r2 = new Role(Role.ADMIN);
        Role r3 = new Role(Role.READER);

        assertThat(r1.getAuthority()).isEqualTo(Role.ADMIN);
        assertEquals(r1, r2);
        assertNotEquals(r1, r3);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void testPasswordValidationRules() {
        // null or empty
        assertThrows(IllegalArgumentException.class, () -> new Password(null));
        assertThrows(IllegalArgumentException.class, () -> new Password(""));

        // less than 8 chars
        assertThrows(IllegalArgumentException.class, () -> new Password("Pass1!"));

        // missing uppercase
        assertThrows(IllegalArgumentException.class, () -> new Password("password123!"));

        // missing lowercase
        assertThrows(IllegalArgumentException.class, () -> new Password("PASSWORD123!"));

        // missing number/special char
        assertThrows(IllegalArgumentException.class, () -> new Password("PasswordOnly"));

        // valid password
        assertDoesNotThrow(() -> new Password("ValidPass1!"));
        assertDoesNotThrow(() -> new Password("Another#Pass99"));
    }
}
