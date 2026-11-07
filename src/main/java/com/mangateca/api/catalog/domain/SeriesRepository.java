package com.mangateca.api.catalog.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeriesRepository extends JpaRepository<Series, UUID> {

	@Query("select s from Series s where s.id = :id and exists "
			+ "(select 1 from Product p where p.series = s and p.status = com.mangateca.api.catalog.domain.PublicationStatus.PUBLISHED)")
	Optional<Series> findVisibleById(@Param("id") UUID id);

	@Query(value = "select x from Series x where exists (select 1 from Product p where p.series = x and p.status = com.mangateca.api.catalog.domain.PublicationStatus.PUBLISHED)",
			countQuery = "select count(x) from Series x where exists (select 1 from Product p where p.series = x and p.status = com.mangateca.api.catalog.domain.PublicationStatus.PUBLISHED)")
	Page<Series> findVisible(Pageable pageable);

}
