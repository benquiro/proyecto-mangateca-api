package com.mangateca.api.catalog.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mangateca.api.catalog.domain.Series;
import com.mangateca.api.catalog.domain.SeriesRepository;

@Service
@Transactional(readOnly = true)
public class ClassificationQueryService {

	private final SeriesRepository seriesRepository;

	public ClassificationQueryService(SeriesRepository seriesRepository) {
		this.seriesRepository = seriesRepository;
	}

	public Series seriesDetail(UUID id) {
		return seriesRepository.findVisibleById(id).orElseThrow(CatalogNotFoundException::new);
	}

}
