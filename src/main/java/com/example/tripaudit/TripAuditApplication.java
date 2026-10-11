package com.example.tripaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TripAuditApplication {

	public static void main(String[] args) {
		SpringApplication.run(TripAuditApplication.class, args);
	}

}
