package com.asmsi.admin_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class AdminSystemApplication {

	public static void main(String[] args) {
		// Render's servers run on UTC. The company, its staff and its biometric machines are on
		// Philippine time, so "now", time logs and every timestamp shown use Asia/Manila.
		TimeZone.setDefault(TimeZone.getTimeZone(System.getProperty("app.timezone",
				System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Manila"))));
		SpringApplication.run(AdminSystemApplication.class, args);
	}

}
