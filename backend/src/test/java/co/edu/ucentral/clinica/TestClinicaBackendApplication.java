package co.edu.ucentral.clinica;

import org.springframework.boot.SpringApplication;

public class TestClinicaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(ClinicaBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
