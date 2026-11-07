package com.mangateca.api.catalog.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GenreRepository extends JpaRepository<Genre, UUID> {

	@Query(value = "select x from Genre x where exists (select 1 from Product p where x member of p.genres and p.status = com.mangateca.api.catalog.domain.PublicationStatus.PUBLISHED)",
			countQuery = "select count(x) from Genre x where exists (select 1 from Product p where x member of p.genres and p.status = com.mangateca.api.catalog.domain.PublicationStatus.PUBLISHED)")
	Page<Genre> findVisible(Pageable pageable);

}
