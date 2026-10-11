package com.example.tripaudit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_number", nullable = false, unique = true)
    private String registrationNumber;

    protected Vehicle() { } // required by JPA

    public Vehicle(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public Long getId() { return id; }
    public String getRegistrationNumber() { return registrationNumber; }
}
