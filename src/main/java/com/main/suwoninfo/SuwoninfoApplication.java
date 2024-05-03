package com.main.suwoninfo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class SuwoninfoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SuwoninfoApplication.class, args);
	}

}
