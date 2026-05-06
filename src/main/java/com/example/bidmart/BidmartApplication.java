package com.example.bidmart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BidmartApplication {

	public static void main(String[] args) {
		SpringApplication.run(BidmartApplication.class, args);
	}

}
