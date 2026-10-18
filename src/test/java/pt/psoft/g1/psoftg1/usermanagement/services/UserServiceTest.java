package pt.psoft.g1.psoftg1.usermanagement.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import pt.psoft.g1.psoftg1.exceptions.ConflictException;
import pt.psoft.g1.psoftg1.shared.model.ForbiddenName;
import pt.psoft.g1.psoftg1.shared.repositories.ForbiddenNameRepository;
import pt.psoft.g1.psoftg1.shared.services.Page;
import pt.psoft.g1.psoftg1.usermanagement.model.Librarian;
import pt.psoft.g1.psoftg1.usermanagement.model.Reader;
import pt.psoft.g1.psoftg1.usermanagement.model.Role;
import pt.psoft.g1.psoftg1.usermanagement.model.User;
import pt.psoft.g1.psoftg1.usermanagement.repositories.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Isolated Unit Test for UserService using Mockito.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private EditUserMapper userEditMapper;

    @Mock
    private ForbiddenNameRepository forbiddenNameRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void testCreate_ThrowsConflict_WhenUsernameAlreadyExists() {
        CreateUserRequest request = new CreateUserRequest("manuel@gmail.com", "Manuel Silva", "Manuelino123!");
        when(userRepo.findByUsername("manuel@gmail.com")).thenReturn(Optional.of(mock(User.class)));

        ConflictException ex = assertThrows(ConflictException.class, () -> userService.create(request));

        assertThat(ex.getMessage()).isEqualTo("Username already exists!");
        verify(userRepo, never()).save(any());
    }

    @Test
    void testCreate_ThrowsException_WhenNameContainsForbiddenWord() {
        CreateUserRequest request = new CreateUserRequest("manuel@gmail.com", "Manuel Voldemort", "Manuelino123!");
        when(userRepo.findByUsername("manuel@gmail.com")).thenReturn(Optional.empty());
        when(forbiddenNameRepository.findByForbiddenNameIsContained("Manuel")).thenReturn(List.of());
        when(forbiddenNameRepository.findByForbiddenNameIsContained("Voldemort"))
                .thenReturn(List.of(mock(ForbiddenName.class)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.create(request));

        assertThat(ex.getMessage()).isEqualTo("Name contains a forbidden word");
        verify(userRepo, never()).save(any());
    }

    @Test
    void testCreate_Reader_Success() {
        CreateUserRequest request = new CreateUserRequest("manuel@gmail.com", "Manuel Silva", "Manuelino123!");
        request.setRole(Role.READER);

        when(userRepo.findByUsername("manuel@gmail.com")).thenReturn(Optional.empty());
        when(forbiddenNameRepository.findByForbiddenNameIsContained(anyString())).thenReturn(List.of());
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = userService.create(request);

        assertThat(created).isNotNull();
        assertThat(created).isInstanceOf(Reader.class);
        assertThat(created.getUsername()).isEqualTo("manuel@gmail.com");
        verify(userRepo).save(any(Reader.class));
    }

    @Test
    void testCreate_Librarian_Success() {
        CreateUserRequest request = new CreateUserRequest("librarian@gmail.com", "Manuel Librarian", "Manuelino123!");
        request.setRole(Role.LIBRARIAN);

        when(userRepo.findByUsername("librarian@gmail.com")).thenReturn(Optional.empty());
        when(forbiddenNameRepository.findByForbiddenNameIsContained(anyString())).thenReturn(List.of());
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = userService.create(request);

        assertThat(created).isNotNull();
        assertThat(created).isInstanceOf(Librarian.class);
        assertThat(created.getUsername()).isEqualTo("librarian@gmail.com");
        verify(userRepo).save(any(Librarian.class));
    }

    @Test
    void testCreate_DefaultRole_ReturnsNull() {
        CreateUserRequest request = new CreateUserRequest("admin@gmail.com", "Admin User", "Admin123!");
        request.setRole(Role.ADMIN);

        when(userRepo.findByUsername("admin@gmail.com")).thenReturn(Optional.empty());
        when(forbiddenNameRepository.findByForbiddenNameIsContained(anyString())).thenReturn(List.of());

        User created = userService.create(request);

        assertThat(created).isNull();
        verify(userRepo, never()).save(any());
    }

    @Test
    void testFindByName() {
        User user = mock(User.class);
        when(userRepo.findByNameName("Manuel")).thenReturn(List.of(user));

        List<User> result = userService.findByName("Manuel");

        assertThat(result).containsExactly(user);
    }

    @Test
    void testFindByNameLike() {
        User user = mock(User.class);
        when(userRepo.findByNameNameContains("Man")).thenReturn(List.of(user));

        List<User> result = userService.findByNameLike("Man");

        assertThat(result).containsExactly(user);
    }

    @Test
    void testUpdate_Success() {
        User user = mock(User.class);
        EditUserRequest request = new EditUserRequest();
        request.setName("New Name");
        when(userRepo.getById(1L)).thenReturn(user);
        when(userRepo.save(user)).thenReturn(user);

        User updated = userService.update(1L, request);

        assertThat(updated).isSameAs(user);
        verify(userEditMapper).update(request, user);
        verify(userRepo).save(user);
    }

    @Test
    void testDelete_SetsEnabledFalse() {
        User user = mock(User.class);
        when(userRepo.getById(1L)).thenReturn(user);
        when(userRepo.save(user)).thenReturn(user);

        User deleted = userService.delete(1L);

        assertThat(deleted).isSameAs(user);
        verify(user).setEnabled(false);
        verify(userRepo).save(user);
    }

    @Test
    void testUsernameExists() {
        when(userRepo.findByUsername("existing@test.com")).thenReturn(Optional.of(mock(User.class)));
        when(userRepo.findByUsername("nonexisting@test.com")).thenReturn(Optional.empty());

        assertThat(userService.usernameExists("existing@test.com")).isTrue();
        assertThat(userService.usernameExists("nonexisting@test.com")).isFalse();
    }

    @Test
    void testGetUser() {
        User user = mock(User.class);
        when(userRepo.getById(10L)).thenReturn(user);

        User result = userService.getUser(10L);

        assertThat(result).isSameAs(user);
    }

    @Test
    void testFindByUsername() {
        User user = mock(User.class);
        when(userRepo.findByUsername("user@test.com")).thenReturn(Optional.of(user));

        Optional<User> result = userService.findByUsername("user@test.com");

        assertThat(result).isPresent().contains(user);
    }

    @Test
    void testSearchUsers_NullArgs_ProvidesDefaults() {
        User user = mock(User.class);
        when(userRepo.searchUsers(any(Page.class), any(SearchUsersQuery.class))).thenReturn(List.of(user));

        List<User> result = userService.searchUsers(null, null);

        assertThat(result).containsExactly(user);
        verify(userRepo).searchUsers(
                argThat(p -> p.getNumber() == 1 && p.getLimit() == 10),
                argThat(q -> "".equals(q.getUsername()) && "".equals(q.getFullName()))
        );
    }

    @Test
    void testLoadUserByUsername_NotFound() {
        when(userRepo.findByUsername("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userService.loadUserByUsername("unknown@example.com"));
    }

    @Test
    void testLoadUserByUsername_Found() {
        User user = mock(User.class);
        when(userRepo.findByUsername("user@example.com")).thenReturn(Optional.of(user));

        UserDetails details = userService.loadUserByUsername("user@example.com");

        assertThat(details).isSameAs(user);
    }

    @Test
    void testGetAuthenticatedUser_ThrowsAccessDenied_WhenAuthenticationNull() {
        assertThrows(AccessDeniedException.class, () -> userService.getAuthenticatedUser(null));
    }

    @Test
    void testGetAuthenticatedUser_ThrowsAccessDenied_WhenPrincipalNotJwt() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("not-a-jwt");

        assertThrows(AccessDeniedException.class, () -> userService.getAuthenticatedUser(auth));
    }

    @Test
    void testGetAuthenticatedUser_ThrowsAccessDenied_WhenUserNotInRepo() {
        Authentication auth = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("sub")).thenReturn("123,ghost@example.com");
        when(userRepo.findByUsername("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> userService.getAuthenticatedUser(auth));
    }

    @Test
    void testGetAuthenticatedUser_Success() {
        Authentication auth = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        when(auth.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("sub")).thenReturn("123,user@example.com");

        User user = mock(User.class);
        when(userRepo.findByUsername("user@example.com")).thenReturn(Optional.of(user));

        User result = userService.getAuthenticatedUser(auth);

        assertThat(result).isSameAs(user);
    }
}
