package com.mangateca.api.catalog.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mangateca.api.catalog.domain.Product;
import com.mangateca.api.catalog.domain.ProductId;
import com.mangateca.api.catalog.domain.ProductRepository;
import com.mangateca.api.catalog.domain.ProductSearchCriteria;
import com.mangateca.api.catalog.domain.ProductSpecifications;

/**
 * Consultas de lectura del catálogo. La paginación se resuelve en BD sobre IDs y después se cargan las entidades.
 */
@Service
@Transactional(readOnly = true)
public class ProductQueryService {

	private final ProductRepository productRepository;

	public ProductQueryService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	public Page<Product> search(ProductSearchCriteria criteria, Pageable pageable) {
		Page<ProductId> idsPage = productRepository.findBy(ProductSpecifications.from(criteria),
				q -> q.as(ProductId.class).page(pageable));
		List<UUID> ids = idsPage.getContent().stream().map(ProductId::getId).toList();
		if (ids.isEmpty()) {
			return new PageImpl<>(List.of(), pageable, idsPage.getTotalElements());
		}
		Map<UUID, Product> byId = productRepository.findAllByIdIn(ids)
			.stream()
			.collect(Collectors.toMap(Product::getId, Function.identity()));
		List<Product> ordered = ids.stream().map(byId::get).toList();
		return new PageImpl<>(ordered, pageable, idsPage.getTotalElements());
	}

}
