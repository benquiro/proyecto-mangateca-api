package com.mangateca.api.catalog.application;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mangateca.api.catalog.domain.Author;
import com.mangateca.api.catalog.domain.AuthorRepository;
import com.mangateca.api.catalog.domain.Genre;
import com.mangateca.api.catalog.domain.GenreRepository;
import com.mangateca.api.catalog.domain.Publisher;
import com.mangateca.api.catalog.domain.PublisherRepository;
import com.mangateca.api.catalog.domain.Series;
import com.mangateca.api.catalog.domain.SeriesRepository;

@Service
@Transactional(readOnly = true)
public class ClassificationQueryService {

	private final GenreRepository genreRepository;

	private final AuthorRepository authorRepository;

	private final PublisherRepository publisherRepository;

	private final SeriesRepository seriesRepository;

	public ClassificationQueryService(GenreRepository genreRepository, AuthorRepository authorRepository,
			PublisherRepository publisherRepository, SeriesRepository seriesRepository) {
		this.genreRepository = genreRepository;
		this.authorRepository = authorRepository;
		this.publisherRepository = publisherRepository;
		this.seriesRepository = seriesRepository;
	}

	public Series seriesDetail(UUID id) {
		return seriesRepository.findVisibleById(id).orElseThrow(CatalogNotFoundException::new);
	}

	public Page<Genre> listGenres(Pageable pageable) {
		return genreRepository.findVisible(fixedOrder(pageable));
	}

	public Page<Author> listAuthors(Pageable pageable) {
		return authorRepository.findVisible(fixedOrder(pageable));
	}

	public Page<Publisher> listPublishers(Pageable pageable) {
		return publisherRepository.findVisible(fixedOrder(pageable));
	}

	public Page<Series> listSeries(Pageable pageable) {
		return seriesRepository.findVisible(fixedOrder(pageable));
	}

	private static Pageable fixedOrder(Pageable pageable) {
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by(Sort.Direction.ASC, "name", "id"));
	}

}
