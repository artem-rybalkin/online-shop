package com.shop.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data               // Lombok: автоматично генерує getters, setters, toString
@NoArgsConstructor  // Lombok: конструктор без параметрів
@AllArgsConstructor // Lombok: конструктор з усіма параметрами
@Builder            // Lombok: додає Builder pattern
@Entity             // JPA: це таблиця в базі даних
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Автоінкремент ID
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;        // Назва товару

    private String description; // Опис товару

    @Column(nullable = false)
    private Double price;       // Ціна

    private Integer stock;      // Кількість на складі

    private String category;    // Категорія (електроніка, одяг тощо)
}