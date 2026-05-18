package com.jspider.spring_boot_simple_crud_with_mysql.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jspider.spring_boot_simple_crud_with_mysql.dao.ProductDao;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.ProductRequestDto;
import com.jspider.spring_boot_simple_crud_with_mysql.entity.Product;
import com.jspider.spring_boot_simple_crud_with_mysql.responses.ResponseStructure;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/product")
@Validated
@Tag(name = "productcontroller", description = "this is controller class")
public class ProductController {

	@Autowired
	ProductDao productDao;

	@Autowired
	ResponseStructure<Product> responseStructure;

	@GetMapping(value = "/getTodayDate")
	public String getTodaysDate() {

		return LocalDate.now() + " ";
	}

	@PreAuthorize("hasRole('USER')")
	@PostMapping(value = "/saveProduct")
	@Operation(description = "it will save one object at a time",
	responses = {
			@ApiResponse(responseCode = "200", description = "Product saved successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid input, object not saved"),
			@ApiResponse(responseCode = "406", description = "Not acceptable, validation failed"),
			@ApiResponse(responseCode = "500", description = "Internal server error") }

	)
	public ResponseStructure<Product> saveProductController(@Valid @RequestBody ProductRequestDto dto) {

		// Mass-assignment defense (AAP Finding #3 / CWE-915): map the validated
		// ProductRequestDto onto a freshly-constructed Product entity. The DTO has no
		// 'id' component, so any client-supplied 'id' in the JSON body is silently
		// dropped by Jackson and cannot influence the persistence-side primary key.
		Product product = new Product();
		product.setName(dto.name());
		product.setColor(dto.color());
		product.setPrice(dto.price());

		System.out.println(product);

		Product product2 = productDao.saveProductDao(product);

		if (product2 != null) {
			responseStructure.setStatusCode(HttpStatus.OK.value());
			responseStructure.setApiDescription("save product Secessfully...");
			responseStructure.setData(product2);
			return responseStructure;
		} else {

			responseStructure.setStatusCode(HttpStatus.NOT_ACCEPTABLE.value());
			responseStructure.setApiDescription("data not saved something went wrong");
			responseStructure.setData(product2);
			return responseStructure;
		}

	}

	@PreAuthorize("hasRole('USER')")
	@PostMapping(value = "/saveProducts")
	public List<Product> saveProductController(@Valid @RequestBody List<ProductRequestDto> dtos) {

		// Validation cascades into each element of the list because the class
		// carries the class-level Spring validation marker (without that marker,
		// only the top-level list reference would be validated). Each
		// ProductRequestDto is mapped onto a new Product entity to preserve the
		// mass-assignment defense (no client-supplied id binding).
		List<Product> products = dtos.stream().map(d -> {
			Product p = new Product();
			p.setName(d.name());
			p.setColor(d.color());
			p.setPrice(d.price());
			return p;
		}).toList();

		System.out.println(products);
		return productDao.saveMultipleProductDao(products);
	}

	@GetMapping(value = "/findAllProduct")
	public List<Product> findAllProductController() {

		return productDao.displayAllProductDao();
	}

	@GetMapping(value = "/getProduct/{id}")
	public Product getProductByIdController(@PathVariable(name = "id") @Positive Integer id) {

		return productDao.getProductByIdDao(id);
	}

	@GetMapping(value = "/getProductByName/{name}")
	public List<Product> getProductByNameDao(@PathVariable(name = "name") String name) {
		return productDao.getProductByNameDao(name);
	}

	@GetMapping(value = "/getProductByPrice/{price}")
	public List<Product> getProductByPriceController(@PathVariable(name = "price") @Positive double price) {
		return productDao.getProductByPriceDao(price);
	}

	@PreAuthorize("hasRole('ADMIN')")
	@DeleteMapping(value = "/deleteProductByPrice/{price}")
	public void deleteProductByPriceController(@PathVariable(name = "price") @Positive double price) {

		productDao.deleteProductByPriceDao(price);
	}
	
	//update
	
	

	@PreAuthorize("hasRole('USER')")
	@PutMapping(value = "/updateProduct/{id}")
	@Operation(description = "it will update one object at a time",
	responses = {
			@ApiResponse(responseCode = "200", description = "Product Update successfully"),
			@ApiResponse(responseCode = "400", description = "Invalid input, object not saved"),
			@ApiResponse(responseCode = "406", description = "Not acceptable, validation failed"),
			@ApiResponse(responseCode = "500", description = "Internal server error") }

	)
	public ResponseStructure<Product> updateProductController(@Valid @RequestBody ProductRequestDto dto, @PathVariable(name = "id") @Positive Integer id) {

		// Mass-assignment defense: build the update payload from the DTO only. The
		// path-variable id (not the DTO) determines which row is updated; the DAO
		// uses that id to look up the existing entity and copies name/color/price
		// only.
		Product userproduct = new Product();
		userproduct.setName(dto.name());
		userproduct.setColor(dto.color());
		userproduct.setPrice(dto.price());

		Product product2 = productDao.updateProductDao(userproduct, id);

		if (product2 != null) {
			responseStructure.setStatusCode(HttpStatus.OK.value());
			responseStructure.setApiDescription("update product Secessfully...");
			responseStructure.setData(product2);
			return responseStructure;
		} else {

			responseStructure.setStatusCode(HttpStatus.NOT_ACCEPTABLE.value());
			responseStructure.setApiDescription("data not saved something went wrong");
			responseStructure.setData(product2);
			return responseStructure;
		}

	}

	
	@PreAuthorize("hasRole('USER')")
	@PutMapping("/{id}")
	public ResponseEntity<Product> updateProduct(@Valid @RequestBody ProductRequestDto dto, @PathVariable @Positive Integer id) {
	    try {
	        Product product = new Product();
	        product.setName(dto.name());
	        product.setColor(dto.color());
	        product.setPrice(dto.price());
	        Product updatedProduct = productDao.updateProductDao(product, id);
	        return new ResponseEntity<Product>(updatedProduct, HttpStatus.OK);
	    } catch (RuntimeException e) {
	        return new ResponseEntity<Product>(HttpStatus.NOT_FOUND); // Make sure this line ends with ;
	    }
	}

	
	
	
 
	
}
