package io.spring.test;

import org.springframework.aot.SpringAotApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RunnerApplication {
	public static void main(String[] args) {
		SpringAotApplication.run(RunnerApplication.class, args);
	}
}