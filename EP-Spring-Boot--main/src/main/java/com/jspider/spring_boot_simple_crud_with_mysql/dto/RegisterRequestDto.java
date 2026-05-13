package com.jspider.spring_boot_simple_crud_with_mysql.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// Rule Applied
public class RegisterRequestDto {

    private String username;
    private String password;
    private String role;
}
