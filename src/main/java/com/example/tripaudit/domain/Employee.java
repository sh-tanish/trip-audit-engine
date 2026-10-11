package com.example.tripaudit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Opaque business id such as EMP-101. Also the EMPLOYEE login username.
    @Column(name = "employee_code", nullable = false, unique = true)
    private String employeeCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    protected Employee() { } // required by JPA

    public Employee(String employeeCode, Gender gender) {
        this.employeeCode = employeeCode;
        this.gender = gender;
    }

    public Long getId() { return id; }
    public String getEmployeeCode() { return employeeCode; }
    public Gender getGender() { return gender; }
}
