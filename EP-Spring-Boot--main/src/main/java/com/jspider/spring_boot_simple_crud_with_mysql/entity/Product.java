package com.jspider.spring_boot_simple_crud_with_mysql.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Entity
@Data
@Schema(name = "product class",description = "this is product entity class")
public class Product {

	@Id
	private int id;

	@NotBlank
	@Size(max = 100)
	private String name;

	@NotBlank
	@Size(max = 50)
	private String color;

	@Schema(description = "price datatype is double")
	@Positive
	@DecimalMax("99999.99")
	private double price;
	 
	
	 
}
