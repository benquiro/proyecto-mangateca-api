package com.mangateca.api.catalog.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

	@EntityGraph(attributePaths = { "authors", "series" })
	List<Product> findAllByIdIn(Collection<UUID> ids);

	@EntityGraph(attributePaths = { "authors", "genres", "publisher", "series" })
	Optional<Product> findWithDetailsByIdAndStatus(UUID id, PublicationStatus status);

}
