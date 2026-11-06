package com.mangateca.api.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "publishers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Publisher {

	@Id
	private UUID id;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

}
