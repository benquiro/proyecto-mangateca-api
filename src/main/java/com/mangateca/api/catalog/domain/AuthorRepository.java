package com.mangateca.api.catalog.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuthorRepository extends JpaRepository<Author, UUID> {

	@Query(value = "select x from Author x where exists (select 1 from Product p where x member of p.authors and p.status = com.mangateca.api.catalog.domain.PublicationStatus.PUBLISHED)",
			countQuery = "select count(x) from Author x where exists (select 1 from Product p where x member of p.authors and p.status = com.mangateca.api.catalog.domain.PublicationStatus.PUBLISHED)")
	Page<Author> findVisible(Pageable pageable);

}
