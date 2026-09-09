package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.Country;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, String> {
}
