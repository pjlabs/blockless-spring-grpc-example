package org.pjlabs.example.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DataSeeder.class);

  private final ProductRepository productRepository;

  public DataSeeder(final ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @Override
  public void run(final String... args) {
    if (productRepository.count() > 0) {
      LOG.info("Data already seeded ({} products)", productRepository.count());
      return;
    }

    final var countries = new String[] {"SE", "US", "GB", "DE", "JP", "BR", "IN", "AU"};
    for (final var country : countries) {
      for (int i = 0; i < 50; i++) {
        productRepository.save(new Product("Product-" + country + "-" + i, country, 100 + i));
      }
    }
    LOG.info("Seeded {} products across {} countries", productRepository.count(), countries.length);
  }
}
