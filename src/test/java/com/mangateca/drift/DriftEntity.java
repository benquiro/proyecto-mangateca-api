package com.mangateca.drift;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad solo de test, sin tabla: provoca el fallo de ddl-auto=validate. Vive fuera de
 * com.mangateca.api para que el escaneo por defecto de la aplicacion no la cargue.
 */
@Entity
@Table(name = "drift_missing_table")
public class DriftEntity {

	@Id
	private Long id;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

}
