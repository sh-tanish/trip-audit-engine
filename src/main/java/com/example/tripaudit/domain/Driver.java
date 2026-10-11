package com.example.tripaudit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "driver")
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_code", nullable = false, unique = true)
    private String driverCode;

    @Column(nullable = false)
    private String name;

    protected Driver() { } // required by JPA

    public Driver(String driverCode, String name) {
        this.driverCode = driverCode;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getDriverCode() { return driverCode; }
    public String getName() { return name; }
}
