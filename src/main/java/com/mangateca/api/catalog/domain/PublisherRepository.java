package com.mangateca.api.catalog.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PublisherRepository extends JpaRepository<Publisher, UUID> {

	@Query(value = "select x from Publisher x where exists (select 1 from Product p where p.publisher = x and p.status = com.mangateca.api.catalog.domain.PublicationStatus.PUBLISHED)",
			countQuery = "select count(x) from Publisher x where exists (select 1 from Product p where p.publisher = x and p.status = com.mangateca.api.catalog.domain.PublicationStatus.PUBLISHED)")
	Page<Publisher> findVisible(Pageable pageable);

}
