package pt.psoft.g1.psoftg1.bookmanagement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookDetailsDTO;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookInfoProvider;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Functional Opaque-Box System Integration Test (SUT = System).
 *
 * <p>Validates the complete end-to-end flow of the Bibliographic Information subsystem
 * across Controller, Service, Repositories, Security, and Gateway Port abstractions,
 * matching the professor's {@code CountryIT} architectural testing patterns.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
public class BookExternalInfoIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookInfoProvider bookInfoProvider;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Test
    @WithMockUser(username = "librarian@example.com", roles = "LIBRARIAN")
    @DisplayName("E2E: Query external bibliographic info and then create book with auto-enrichment")
    void testEndToEnd_ExternalQueryAndAutoEnrichedCreation() throws Exception {
        // Valid ISBN-13 not yet in database
        String testIsbn = "9780201633610";

        Author author = authorRepository.save(new Author("Manuel Antonio Pina", "Author Bio", null));
        Genre genre = genreRepository.save(new Genre("Software"));

        BookDetailsDTO mockExternalData = new BookDetailsDTO(
                testIsbn,
                "Design Patterns E2E",
                "Elements of Reusable Object-Oriented Software",
                List.of("Erich Gamma"),
                "Software",
                "Addison-Wesley",
                "1994",
                null,
                "googlebooks+openlibrary"
        );

        given(bookInfoProvider.findBookByIsbn(testIsbn)).willReturn(Optional.of(mockExternalData));

        // Step 1: Query external bibliographic metadata endpoint
        mockMvc.perform(get("/api/books/external/" + testIsbn)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value(testIsbn))
                .andExpect(jsonPath("$.title").value("Design Patterns E2E"))
                .andExpect(jsonPath("$.genre").value("Software"))
                .andExpect(jsonPath("$.publisher").value("Addison-Wesley"))
                .andExpect(jsonPath("$.sourceProvider").value("googlebooks+openlibrary"));

        // Step 2: Create a new book omitting title, description, and genre (auto-enriched via external provider)
        mockMvc.perform(put("/api/books/" + testIsbn)
                        .param("authors", String.valueOf(author.getAuthorNumber())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").value(testIsbn))
                .andExpect(jsonPath("$.title").value("Design Patterns E2E"))
                .andExpect(jsonPath("$.description").value("Elements of Reusable Object-Oriented Software"))
                .andExpect(jsonPath("$.genre").value("Software"));

        // Step 3: Verify the newly created book is now queryable from internal library catalog
        mockMvc.perform(get("/api/books/" + testIsbn)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value(testIsbn))
                .andExpect(jsonPath("$.title").value("Design Patterns E2E"));
    }

    @Test
    @WithMockUser(username = "reader@example.com", roles = "READER")
    @DisplayName("E2E: Query external bibliographic info when ISBN is unknown returns 404")
    void testEndToEnd_ExternalQuery_NotFoundReturns404() throws Exception {
        String unknownIsbn = "9780000000999";
        given(bookInfoProvider.findBookByIsbn(unknownIsbn)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/books/external/" + unknownIsbn)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}

