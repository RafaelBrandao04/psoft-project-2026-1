package pt.psoft.g1.psoftg1.external.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Based on https://www.baeldung.com/spring-5-webclient
 */
@Service
@RequiredArgsConstructor
public class ApiNinjasService {
    private final WebClient webClient;

    private List<HistoricalEventsResponse> getHistoricalEventsFromYearMonth(int year, int month) {
        return webClient.get()
                .uri("historicalevents?year=" + year + "&month=" + month)
                .retrieve()
                .bodyToFlux(HistoricalEventsResponse.class)
                .collectList()
                .block();
    }

    public String getRandomEventFromYearMonth(int year, int month) {
        try {
            final var responseList = getHistoricalEventsFromYearMonth(year, month);
            if (responseList != null && !responseList.isEmpty()) {
                int randomIndex = (int) (Math.random() * responseList.size());
                return responseList.get(randomIndex).getEvent();
            }
        } catch (Exception ex) {
            // Fallback if external API is unreachable or returns an error (rate limits, 403, network, etc.)
        }
        return "Historical event for " + year + "/" + month;
    }
}
