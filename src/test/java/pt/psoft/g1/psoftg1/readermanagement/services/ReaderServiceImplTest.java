package pt.psoft.g1.psoftg1.readermanagement.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import pt.psoft.g1.psoftg1.exceptions.ConflictException;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;
import pt.psoft.g1.psoftg1.readermanagement.repositories.ReaderRepository;
import pt.psoft.g1.psoftg1.shared.model.ForbiddenName;
import pt.psoft.g1.psoftg1.shared.model.Photo;
import pt.psoft.g1.psoftg1.shared.repositories.ForbiddenNameRepository;
import pt.psoft.g1.psoftg1.shared.repositories.PhotoRepository;
import pt.psoft.g1.psoftg1.shared.services.Page;
import pt.psoft.g1.psoftg1.usermanagement.model.Reader;
import pt.psoft.g1.psoftg1.usermanagement.model.User;
import pt.psoft.g1.psoftg1.usermanagement.repositories.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Isolated Unit Test for ReaderServiceImpl using Mockito.
 */
@ExtendWith(MockitoExtension.class)
class ReaderServiceImplTest {

    @Mock
    private ReaderRepository readerRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private ReaderMapper readerMapper;

    @Mock
    private GenreRepository genreRepo;

    @Mock
    private ForbiddenNameRepository forbiddenNameRepository;

    @Mock
    private PhotoRepository photoRepository;

    @InjectMocks
    private ReaderServiceImpl readerService;

    @Test
    void testCreate_ThrowsConflict_WhenUsernameAlreadyExists() {
        CreateReaderRequest request = new CreateReaderRequest();
        request.setUsername("reader@example.com");
        request.setFullName("John Doe");

        when(userRepo.findByUsername("reader@example.com")).thenReturn(Optional.of(mock(User.class)));

        ConflictException ex = assertThrows(ConflictException.class, () -> readerService.create(request, null));

        assertThat(ex.getMessage()).isEqualTo("Username already exists!");
        verify(readerRepo, never()).save(any());
    }

    @Test
    void testCreate_ThrowsException_WhenNameContainsForbiddenWord() {
        CreateReaderRequest request = new CreateReaderRequest();
        request.setUsername("reader@example.com");
        request.setFullName("John Voldemort");

        when(userRepo.findByUsername("reader@example.com")).thenReturn(Optional.empty());
        when(forbiddenNameRepository.findByForbiddenNameIsContained("John")).thenReturn(List.of());
        when(forbiddenNameRepository.findByForbiddenNameIsContained("Voldemort"))
                .thenReturn(List.of(mock(ForbiddenName.class)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> readerService.create(request, null));

        assertThat(ex.getMessage()).isEqualTo("Name contains a forbidden word");
        verify(readerRepo, never()).save(any());
    }

    @Test
    void testCreate_ThrowsNotFound_WhenInterestGenreNotFound() {
        CreateReaderRequest request = new CreateReaderRequest();
        request.setUsername("reader@example.com");
        request.setFullName("John Doe");
        request.setInterestList(List.of("NonExistentGenre"));

        when(userRepo.findByUsername("reader@example.com")).thenReturn(Optional.empty());
        when(forbiddenNameRepository.findByForbiddenNameIsContained(anyString())).thenReturn(List.of());
        when(genreRepo.findByString("NonExistentGenre")).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class, () -> readerService.create(request, null));

        assertThat(ex.getMessage()).contains("Could not find genre with name NonExistentGenre");
    }

    @Test
    void testCreate_Success() {
        CreateReaderRequest request = new CreateReaderRequest();
        request.setUsername("reader@example.com");
        request.setFullName("John Doe");
        request.setInterestList(List.of("Sci-Fi"));

        Genre genre = new Genre("Sci-Fi");
        Reader reader = mock(Reader.class);
        ReaderDetails readerDetails = mock(ReaderDetails.class);

        when(userRepo.findByUsername("reader@example.com")).thenReturn(Optional.empty());
        when(forbiddenNameRepository.findByForbiddenNameIsContained(anyString())).thenReturn(List.of());
        when(genreRepo.findByString("Sci-Fi")).thenReturn(Optional.of(genre));
        when(readerRepo.getCountFromCurrentYear()).thenReturn(10);
        when(readerMapper.createReader(request)).thenReturn(reader);
        when(readerMapper.createReaderDetails(eq(11), eq(reader), eq(request), isNull(), any())).thenReturn(readerDetails);
        when(readerRepo.save(readerDetails)).thenReturn(readerDetails);

        ReaderDetails result = readerService.create(request, null);

        assertThat(result).isSameAs(readerDetails);
        verify(userRepo).save(reader);
        verify(readerRepo).save(readerDetails);
    }

    @Test
    void testCreate_InconsistentPhoto_ResetsPhoto() {
        CreateReaderRequest request = new CreateReaderRequest();
        request.setUsername("reader@example.com");
        request.setFullName("John Doe");
        MultipartFile mockFile = mock(MultipartFile.class);
        request.setPhoto(mockFile);

        Reader reader = mock(Reader.class);
        ReaderDetails readerDetails = mock(ReaderDetails.class);

        when(userRepo.findByUsername("reader@example.com")).thenReturn(Optional.empty());
        when(forbiddenNameRepository.findByForbiddenNameIsContained(anyString())).thenReturn(List.of());
        when(readerRepo.getCountFromCurrentYear()).thenReturn(5);
        when(readerMapper.createReader(request)).thenReturn(reader);
        when(readerMapper.createReaderDetails(eq(6), eq(reader), eq(request), isNull(), isNull())).thenReturn(readerDetails);
        when(readerRepo.save(readerDetails)).thenReturn(readerDetails);

        ReaderDetails result = readerService.create(request, null);

        assertThat(result).isSameAs(readerDetails);
        assertThat(request.getPhoto()).isNull();
    }

    @Test
    void testFindTopByGenre_ThrowsException_WhenStartDateAfterEndDate() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 1, 1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> readerService.findTopByGenre("Fantasy", start, end));

        assertThat(ex.getMessage()).isEqualTo("Start date cannot be after end date");
    }

    @Test
    void testFindTopByGenre_Success() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        ReaderBookCountDTO dto = mock(ReaderBookCountDTO.class);
        org.springframework.data.domain.Page<ReaderBookCountDTO> page = new PageImpl<>(List.of(dto));

        when(readerRepo.findTopByGenre(any(Pageable.class), eq("Fantasy"), eq(start), eq(end)))
                .thenReturn(page);

        List<ReaderBookCountDTO> result = readerService.findTopByGenre("Fantasy", start, end);

        assertThat(result).containsExactly(dto);
    }

    @Test
    void testUpdate_ThrowsNotFound_WhenReaderNotFound() {
        UpdateReaderRequest request = new UpdateReaderRequest();
        when(readerRepo.findByUserId(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> readerService.update(99L, request, 1L, null));
    }

    @Test
    void testUpdate_Success() {
        UpdateReaderRequest request = new UpdateReaderRequest();
        request.setInterestList(List.of("Horror"));
        Genre genre = new Genre("Horror");
        ReaderDetails rd = mock(ReaderDetails.class);
        Reader reader = mock(Reader.class);

        when(readerRepo.findByUserId(1L)).thenReturn(Optional.of(rd));
        when(genreRepo.findByString("Horror")).thenReturn(Optional.of(genre));
        when(rd.getReader()).thenReturn(reader);
        when(readerRepo.save(rd)).thenReturn(rd);

        ReaderDetails updated = readerService.update(1L, request, 1L, "photo.jpg");

        assertThat(updated).isSameAs(rd);
        verify(rd).applyPatch(eq(1L), eq(request), eq("photo.jpg"), any());
        verify(userRepo).save(reader);
        verify(readerRepo).save(rd);
    }

    @Test
    void testFindByReaderNumber() {
        ReaderDetails rd = mock(ReaderDetails.class);
        when(readerRepo.findByReaderNumber("2026/1")).thenReturn(Optional.of(rd));

        Optional<ReaderDetails> result = readerService.findByReaderNumber("2026/1");

        assertThat(result).isPresent().contains(rd);
    }

    @Test
    void testFindByPhoneNumber() {
        ReaderDetails rd = mock(ReaderDetails.class);
        when(readerRepo.findByPhoneNumber("912345678")).thenReturn(List.of(rd));

        List<ReaderDetails> result = readerService.findByPhoneNumber("912345678");

        assertThat(result).containsExactly(rd);
    }

    @Test
    void testFindByUsername() {
        ReaderDetails rd = mock(ReaderDetails.class);
        when(readerRepo.findByUsername("reader@example.com")).thenReturn(Optional.of(rd));

        Optional<ReaderDetails> result = readerService.findByUsername("reader@example.com");

        assertThat(result).isPresent().contains(rd);
    }

    @Test
    void testFindAll() {
        ReaderDetails rd = mock(ReaderDetails.class);
        when(readerRepo.findAll()).thenReturn(List.of(rd));

        Iterable<ReaderDetails> result = readerService.findAll();

        assertThat(result).containsExactly(rd);
    }

    @Test
    void testFindTopReaders_ThrowsException_WhenMinTopLessThan1() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> readerService.findTopReaders(0));

        assertThat(ex.getMessage()).isEqualTo("Minimum top reader must be greater than 0");
    }

    @Test
    void testFindTopReaders_Success() {
        ReaderDetails rd = mock(ReaderDetails.class);
        org.springframework.data.domain.Page<ReaderDetails> page = new PageImpl<>(List.of(rd));
        when(readerRepo.findTopReaders(any(Pageable.class))).thenReturn(page);

        List<ReaderDetails> result = readerService.findTopReaders(5);

        assertThat(result).containsExactly(rd);
    }

    @Test
    void testRemoveReaderPhoto_ThrowsNotFound_WhenReaderNotFound() {
        when(readerRepo.findByReaderNumber("2026/1")).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> readerService.removeReaderPhoto("2026/1", 1L));

        assertThat(ex.getMessage()).isEqualTo("Cannot find reader");
    }

    @Test
    void testRemoveReaderPhoto_Success() {
        ReaderDetails rd = mock(ReaderDetails.class);
        Photo photo = mock(Photo.class);
        when(readerRepo.findByReaderNumber("2026/1")).thenReturn(Optional.of(rd));
        when(rd.getPhoto()).thenReturn(photo);
        when(photo.getPhotoFile()).thenReturn("reader_photo.jpg");
        when(readerRepo.save(rd)).thenReturn(rd);

        Optional<ReaderDetails> result = readerService.removeReaderPhoto("2026/1", 1L);

        assertThat(result).isPresent().contains(rd);
        verify(rd).removePhoto(1L);
        verify(photoRepository).deleteByPhotoFile("reader_photo.jpg");
        verify(readerRepo).save(rd);
    }

    @Test
    void testSearchReaders_ThrowsNotFound_WhenNoResults() {
        when(readerRepo.searchReaderDetails(any(Page.class), any(SearchReadersQuery.class)))
                .thenReturn(List.of());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> readerService.searchReaders(null, null));

        assertThat(ex.getMessage()).isEqualTo("No results match the search query");
    }

    @Test
    void testSearchReaders_Success() {
        ReaderDetails rd = mock(ReaderDetails.class);
        when(readerRepo.searchReaderDetails(any(Page.class), any(SearchReadersQuery.class)))
                .thenReturn(List.of(rd));

        List<ReaderDetails> result = readerService.searchReaders(null, null);

        assertThat(result).containsExactly(rd);
    }
}
