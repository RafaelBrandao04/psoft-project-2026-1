package pt.psoft.g1.psoftg1.lendingmanagement.repositories;

import pt.psoft.g1.psoftg1.lendingmanagement.model.Lending;
import pt.psoft.g1.psoftg1.shared.services.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LendingRepository {
    Optional<Lending> findByLendingNumber(String lendingNumber);
    List<Lending> listByReaderNumberAndIsbn(String readerNumber, String isbn);
    int getCountFromCurrentYear();
    List<Lending> listOutstandingByReaderNumber(String readerNumber);
    default List<Lending> listByReaderNumber(String readerNumber) {
        return searchLendings(new Page(1, 1000), readerNumber, null, null, null, null);
    }
    Double getAverageDuration();
    Double getAvgLendingDurationByIsbn(String isbn);


    List<Lending> getOverdue(Page page);
    List<Lending> searchLendings(Page page, String readerNumber, String isbn, Boolean returned, LocalDate startDate, LocalDate endDate);

    Lending save(Lending lending);

    void delete(Lending lending);

}
