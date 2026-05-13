package org.pjlabs.example.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

  List<Product> findByCountry(String country);

  @Query("SELECT COUNT(p) FROM Product p WHERE p.country = :country")
  int countByCountry(@Param("country") String country);

  @Query(
      value = "SELECT COUNT(*) FROM products WHERE country = :country AND (SELECT pg_sleep(:sleepSeconds)) IS NOT NULL",
      nativeQuery = true)
  int countByCountryWithDelay(@Param("country") String country, @Param("sleepSeconds") double sleepSeconds);
}
