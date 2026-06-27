package com.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.shop.model.Product;
import com.shop.model.User;
import com.shop.repository.UserRepository;
import com.shop.service.ProductService;

@SpringBootApplication
public class OnlineShopApplication {

	public static void main(String[] args) {
		SpringApplication.run(OnlineShopApplication.class, args);
	}

	@Bean
	@org.springframework.context.annotation.Profile("!test")
	@SuppressWarnings("null")
	public CommandLineRunner demo(ProductService productService,
	                              UserRepository userRepository,
	                              PasswordEncoder passwordEncoder) {
		return (args) -> {
			if (userRepository.findByUsername("admin").isEmpty()) {
				User admin = User.builder()
						.username("admin")
						.email("admin@shop.local")
						.password(passwordEncoder.encode("admin"))
						.role("ADMIN")
						.build();
				userRepository.save(admin);
			}

			if (productService.getAllProducts(org.springframework.data.domain.PageRequest.of(0, 1)).isEmpty()) {
				productService.createProduct(Product.builder()
						.name("iPhone 14")
						.description("Latest Apple smartphone")
						.price(35000.0)
						.stock(50)
						.category("Electronics")
						.build());

				productService.createProduct(Product.builder()
						.name("MacBook Air")
						.description("Slim and powerful laptop")
						.price(45000.0)
						.stock(20)
						.category("Electronics")
						.build());

				productService.createProduct(Product.builder()
						.name("Sony WH-1000XM4")
						.description("Noise cancelling headphones")
						.price(12000.0)
						.stock(30)
						.category("Electronics")
						.build());

				productService.createProduct(Product.builder()
						.name("Nike Air Max")
						.description("Comfortable running shoes")
						.price(4000.0)
						.stock(100)
						.category("Clothing")
						.build());

				productService.createProduct(Product.builder()
						.name("Levis 501")
						.description("Classic denim jeans")
						.price(2500.0)
						.stock(80)
						.category("Clothing")
						.build());

				productService.createProduct(Product.builder()
						.name("Samsung Galaxy S23")
						.description("Flagship Android smartphone")
						.price(32000.0)
						.stock(40)
						.category("Electronics")
						.build());

				productService.createProduct(Product.builder()
						.name("Adidas Ultraboost")
						.description("Premium running shoes")
						.price(5500.0)
						.stock(60)
						.category("Clothing")
						.build());

				productService.createProduct(Product.builder()
						.name("Coffee Maker")
						.description("Programmable 12-cup coffee brewer")
						.price(2500.0)
						.stock(15)
						.category("Home")
						.build());

				productService.createProduct(Product.builder()
						.name("Desk Lamp")
						.description("LED lamp with wireless charging base")
						.price(1200.0)
						.stock(30)
						.category("Home")
						.build());
			}
		};
	}
}
