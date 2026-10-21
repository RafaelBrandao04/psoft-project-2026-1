package pt.psoft.g1.psoftg1.bookmanagement.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import org.springframework.web.multipart.MultipartFile;
import pt.psoft.g1.psoftg1.authormanagement.model.Author;
import pt.psoft.g1.psoftg1.bookmanagement.model.*;
import pt.psoft.g1.psoftg1.bookmanagement.repositories.BookRepository;
import lombok.RequiredArgsConstructor;
import pt.psoft.g1.psoftg1.genremanagement.repositories.GenreRepository;
import pt.psoft.g1.psoftg1.authormanagement.repositories.AuthorRepository;
import pt.psoft.g1.psoftg1.exceptions.ConflictException;
import pt.psoft.g1.psoftg1.exceptions.NotFoundException;
import pt.psoft.g1.psoftg1.genremanagement.model.Genre;
import pt.psoft.g1.psoftg1.readermanagement.model.ReaderDetails;
import pt.psoft.g1.psoftg1.readermanagement.repositories.ReaderRepository;
import pt.psoft.g1.psoftg1.shared.repositories.PhotoRepository;
import pt.psoft.g1.psoftg1.shared.services.Page;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@PropertySource({"classpath:config/library.properties"})
public class BookServiceImpl implements BookService {

	private final BookRepository bookRepository;
	private final GenreRepository genreRepository;
	private final AuthorRepository authorRepository;
	private final PhotoRepository photoRepository;
	private final ReaderRepository readerRepository;
	private final BookInfoProvider bookInfoProvider;

	@Value("${suggestionsLimitPerGenre}")
	private long suggestionsLimitPerGenre;

	@Override
	public Book create(CreateBookRequest request, String isbn) {

		if(bookRepository.findByIsbn(isbn).isPresent()){
			throw new ConflictException("Book with ISBN " + isbn + " already exists");
		}

		enrichRequestFromExternalSourcesIfNeeded(request, isbn);

		List<Long> authorNumbers = request.getAuthors() != null ? request.getAuthors() : List.of();
		List<Author> authors = new ArrayList<>();
		for (Long authorNumber : authorNumbers) {

			Optional<Author> temp = authorRepository.findByAuthorNumber(authorNumber);
			if(temp.isEmpty()) {
				continue;
			}

			Author author = temp.get();
			authors.add(author);
		}

		MultipartFile photo = request.getPhoto();
		String photoURI = request.getPhotoURI();
		if(photo == null && photoURI != null || photo != null && photoURI == null) {
			request.setPhoto(null);
			request.setPhotoURI(null);
		}

		final var genre = genreRepository.findByString(request.getGenre())
				.orElseThrow(() -> new NotFoundException("Genre not found"));

		Book newBook = new Book(isbn, request.getTitle(), request.getDescription(), genre, authors, photoURI);

        return bookRepository.save(newBook);
	}


	@Override
	public Book update(UpdateBookRequest request, String currentVersion) {

        var book = findByIsbn(request.getIsbn());
        if(request.getAuthors()!= null) {
            List<Long> authorNumbers = request.getAuthors();
            List<Author> authors = new ArrayList<>();
            for (Long authorNumber : authorNumbers) {
                Optional<Author> temp = authorRepository.findByAuthorNumber(authorNumber);
                if (temp.isEmpty()) {
                    continue;
                }
                Author author = temp.get();
                authors.add(author);
            }

            request.setAuthorObjList(authors);
        }

		MultipartFile photo = request.getPhoto();
		String photoURI = request.getPhotoURI();
		if(photo == null && photoURI != null || photo != null && photoURI == null) {
			request.setPhoto(null);
			request.setPhotoURI(null);
		}

        if (request.getGenre() != null) {
            Optional<Genre> genre = genreRepository.findByString(request.getGenre());
            if (genre.isEmpty()) {
                throw new NotFoundException("Genre not found");
            }
            request.setGenreObj(genre.get());
        }

        book.applyPatch(Long.parseLong(currentVersion), request);

		bookRepository.save(book);


		return book;
	}

	@Override
	public Book save(Book book) {
		return this.bookRepository.save(book);
	}

	@Override
	public List<BookCountDTO> findTop5BooksLent(){
		LocalDate oneYearAgo = LocalDate.now().minusYears(1);
		Pageable pageableRules = PageRequest.of(0,5);
		return this.bookRepository.findTop5BooksLent(oneYearAgo, pageableRules).getContent();
	}

	@Override
	public Book removeBookPhoto(String isbn, long desiredVersion) {
		Book book = this.findByIsbn(isbn);
		String photoFile;
		try {
			photoFile = book.getPhoto().getPhotoFile();
		}catch (NullPointerException e){
			throw new NotFoundException("Book did not have a photo assigned to it.");
		}

		book.removePhoto(desiredVersion);
		var updatedBook = bookRepository.save(book);
		photoRepository.deleteByPhotoFile(photoFile);
		return updatedBook;
	}

	@Override
	public List<Book> findByGenre(String genre) {
		return this.bookRepository.findByGenre(genre);
	}

	public List<Book> findByTitle(String title) {
		return bookRepository.findByTitle(title);
	}

	@Override
	public List<Book> findByAuthorName(String authorName) {
		return bookRepository.findByAuthorName(authorName + "%");
	}

	public Book findByIsbn(String isbn) {
		return this.bookRepository.findByIsbn(isbn)
				.orElseThrow(() -> new NotFoundException(Book.class, isbn));
	}

	public List<Book> getBooksSuggestionsForReader(String readerNumber) {
		List<Book> books = new ArrayList<>();

		ReaderDetails readerDetails = readerRepository.findByReaderNumber(readerNumber)
				.orElseThrow(() -> new NotFoundException("Reader not found with provided login"));
		List<Genre> interestList = readerDetails.getInterestList();

		if(interestList.isEmpty()) {
			throw new NotFoundException("Reader has no interests");
		}

		for(Genre genre : interestList) {
			List<Book> tempBooks = bookRepository.findByGenre(genre.toString());
			if(tempBooks.isEmpty()) {
				continue;
			}

			long genreBookCount = 0;

            for (Book loopBook : tempBooks) {
                if (genreBookCount >= suggestionsLimitPerGenre) {
                    break;
                }

                books.add(loopBook);
				genreBookCount++;
            }
		}

		return books;
	}

	@Override
	public List<Book> searchBooks(Page page, SearchBooksQuery query) {
		if (page == null) {
			page = new Page(1, 10);
		}
		if (query == null) {
			query = new SearchBooksQuery("", "", "");
		}
		return bookRepository.searchBooks(page, query);
	}

	@Override
	public Optional<BookDetailsDTO> findExternalBookInfo(String isbn) {
		if (bookInfoProvider == null) {
			return Optional.empty();
		}
		return bookInfoProvider.findBookByIsbn(isbn);
	}

	private void enrichRequestFromExternalSourcesIfNeeded(CreateBookRequest request, String isbn) {
		if (request == null || bookInfoProvider == null || !needsExternalEnrichment(request)) {
			return;
		}

		Optional<BookDetailsDTO> externalOpt = bookInfoProvider.findBookByIsbn(isbn);
		if (externalOpt.isEmpty()) {
			return;
		}

		BookDetailsDTO external = externalOpt.get();
		if (isBlank(request.getTitle()) && external.getTitle() != null) {
			request.setTitle(external.getTitle());
		}
		if (isBlank(request.getDescription()) && external.getDescription() != null) {
			request.setDescription(external.getDescription());
		}
		if (isBlank(request.getGenre()) && external.getGenre() != null) {
			request.setGenre(external.getGenre());
		}
		if ((request.getAuthors() == null || request.getAuthors().isEmpty()) && !external.getAuthors().isEmpty()) {
			List<Long> resolvedAuthorIds = new ArrayList<>();
			for (String authorName : external.getAuthors()) {
				List<Author> matched = authorRepository.searchByNameName(authorName);
				if (matched != null && !matched.isEmpty() && matched.get(0).getAuthorNumber() != null) {
					resolvedAuthorIds.add(matched.get(0).getAuthorNumber());
				}
			}
			if (!resolvedAuthorIds.isEmpty()) {
				request.setAuthors(resolvedAuthorIds);
			}
		}
	}

	private static boolean needsExternalEnrichment(CreateBookRequest request) {
		return isBlank(request.getTitle())
				|| isBlank(request.getDescription())
				|| isBlank(request.getGenre())
				|| request.getAuthors() == null
				|| request.getAuthors().isEmpty();
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
}
