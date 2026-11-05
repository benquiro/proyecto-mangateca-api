package com.mangateca.drift;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = DriftEntity.class)
public class DriftEntityConfiguration {

}
