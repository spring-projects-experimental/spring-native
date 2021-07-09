package com.example.commandlinerunner;

import java.lang.reflect.InvocationTargetException;

import org.springframework.aot.SpringAotApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CommandlinerunnerApplication {

	public static void main(String[] args) throws InterruptedException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		SpringAotApplication.run(CommandlinerunnerApplication.class, args);
		Thread.currentThread().join(); // To be able to measure memory consumption
	}
	
}
