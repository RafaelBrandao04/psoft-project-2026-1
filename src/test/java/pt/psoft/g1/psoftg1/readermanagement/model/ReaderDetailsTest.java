package pt.psoft.g1.psoftg1.readermanagement.model;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pt.psoft.g1.psoftg1.exceptions.ConflictException;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.readermanagement.services.UpdateReaderRequest;
import pt.psoft.g1.psoftg1.usermanagement.model.Reader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ReaderDetailsTest {

    @Test
    void testConstructor_Success() {
        Reader reader = new Reader("reader@example.com", "Password123!");
        Genre genre = new Genre("Fantasy");
        ReaderDetails rd = new ReaderDetails(1, reader, "2000-01-01", "912345678", true, true, false, "photo.jpg", List.of(genre));

        assertThat(rd.getReader()).isSameAs(reader);
        assertThat(rd.getReaderNumber()).contains("/1");
        assertThat(rd.getBirthDate().toString()).isEqualTo("2000-1-1");
        assertThat(rd.getPhoneNumber()).isEqualTo("912345678");
        assertTrue(rd.isGdprConsent());
        assertTrue(rd.isMarketingConsent());
        assertFalse(rd.isThirdPartySharingConsent());
        assertThat(rd.getPhoto().getPhotoFile()).isEqualTo("photo.jpg");
        assertThat(rd.getInterestList()).containsExactly(genre);
    }

    @Test
    void testConstructor_NullReaderOrPhone_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReaderDetails(1, null, "2000-01-01", "912345678", true, false, false, null, null));

        Reader reader = new Reader("reader@example.com", "Password123!");
        assertThrows(IllegalArgumentException.class,
                () -> new ReaderDetails(1, reader, "2000-01-01", null, true, false, false, null, null));
    }

    @Test
    void testConstructor_NoGdpr_ThrowsException() {
        Reader reader = new Reader("reader@example.com", "Password123!");
        assertThrows(IllegalArgumentException.class,
                () -> new ReaderDetails(1, reader, "2000-01-01", "912345678", false, false, false, null, null));
    }

    @Test
    void testApplyPatch_ThrowsConflict_WhenVersionMismatch() {
        Reader reader = new Reader("reader@example.com", "Password123!");
        ReaderDetails rd = new ReaderDetails(1, reader, "2000-01-01", "912345678", true, false, false, null, null);
        ReflectionTestUtils.setField(rd, "version", 2L);

        UpdateReaderRequest request = new UpdateReaderRequest();
        assertThrows(ConflictException.class, () -> rd.applyPatch(1L, request, null, null));
    }

    @Test
    void testApplyPatch_Success_UpdatesFields() {
        Reader reader = new Reader("reader@example.com", "Password123!");
        ReaderDetails rd = new ReaderDetails(1, reader, "2000-01-01", "912345678", true, false, false, null, null);
        ReflectionTestUtils.setField(rd, "version", 1L);

        UpdateReaderRequest request = new UpdateReaderRequest();
        request.setBirthDate("1995-05-15");
        request.setPhoneNumber("933333333");
        request.setMarketing(true);
        request.setThirdParty(true);
        request.setUsername("newreader@example.com");
        request.setPassword("NewPass123!");
        request.setFullName("Updated Name");

        Genre newGenre = new Genre("History");
        rd.applyPatch(1L, request, "new_photo.jpg", List.of(newGenre));

        assertThat(rd.getBirthDate().toString()).isEqualTo("1995-5-15");
        assertThat(rd.getPhoneNumber()).isEqualTo("933333333");
        assertTrue(rd.isMarketingConsent());
        assertTrue(rd.isThirdPartySharingConsent());
        assertThat(rd.getReader().getUsername()).isEqualTo("newreader@example.com");
        assertThat(rd.getInterestList()).containsExactly(newGenre);
        assertThat(rd.getPhoto().getPhotoFile()).isEqualTo("new_photo.jpg");
    }

    @Test
    void testRemovePhoto_VersionConflict_ThrowsException() {
        Reader reader = new Reader("reader@example.com", "Password123!");
        ReaderDetails rd = new ReaderDetails(1, reader, "2000-01-01", "912345678", true, false, false, "photo.jpg", null);
        ReflectionTestUtils.setField(rd, "version", 2L);

        assertThrows(ConflictException.class, () -> rd.removePhoto(1L));
    }

    @Test
    void testRemovePhoto_Success() {
        Reader reader = new Reader("reader@example.com", "Password123!");
        ReaderDetails rd = new ReaderDetails(1, reader, "2000-01-01", "912345678", true, false, false, "photo.jpg", null);
        ReflectionTestUtils.setField(rd, "version", 1L);

        rd.removePhoto(1L);

        assertNull(rd.getPhoto());
    }
}
