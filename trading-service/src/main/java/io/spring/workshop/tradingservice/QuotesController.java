package io.spring.workshop.tradingservice;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestTemplate;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Controller
public class QuotesController {

	private final TradingCompanyService tradingCompanyService;

	private final ScheduledExecutorService executorService;

	private final RestTemplate restTemplate;

	public QuotesController(TradingCompanyService tradingCompanyService, RestTemplateBuilder restTemplateBuilder) {
		this.tradingCompanyService = tradingCompanyService;
		this.restTemplate = restTemplateBuilder.build();
		this.executorService = Executors.newSingleThreadScheduledExecutor();
	}

	@GetMapping(path = "/quotes/feed", produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	@SuppressWarnings("unchecked")
	public List<?> latestQuotes() throws Exception {
		RequestEntity requestEntity = RequestEntity.get(new URI("http://localhost:8081/quotes?take=7"))
				.accept(APPLICATION_JSON)
				.build();

		return this.restTemplate
				.exchange(requestEntity, List.class)
				.getBody();
	}

	@GetMapping(path = "/quotes/summary/{ticker}", produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public TradingCompanySummary quotesDetails(@PathVariable String ticker) throws Exception {
		RequestEntity requestEntity = RequestEntity.get(new URI("http://localhost:8081/quotes?take=7"))
				.accept(APPLICATION_JSON)
				.build();


		TradingCompany company = this.tradingCompanyService.getTradingCompany(ticker);

		if (company == null) {
			throw new TickerNotFoundException("Unknown ticker: "+ticker);
		}

		AtomicBoolean timeout = new AtomicBoolean(false);

		ScheduledFuture<?> f = executorService.schedule(() -> timeout.set(true), 15, TimeUnit.SECONDS);

		Quote match = null;
		while (match == null && !timeout.get()) {

			List<Quote> latest = this.restTemplate.exchange(requestEntity, new ParameterizedTypeReference<List<Quote>>() {}).getBody();

			match = latest.stream()
					.filter(q -> q.getTicker().equalsIgnoreCase(ticker))
					.findAny()
					.orElse(null);
		}
		f.cancel(true);

		if (match == null) {
			match = new Quote(ticker);
		}

		return new TradingCompanySummary(match, company);

	}

	@ResponseStatus(HttpStatus.NOT_FOUND)
	@ExceptionHandler(TickerNotFoundException.class)
	public void onTickerNotFound() {
	}

}