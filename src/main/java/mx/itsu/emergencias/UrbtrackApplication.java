package mx.itsu.emergencias;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UrbtrackApplication {

	public static void main(String[] args) {
		SpringApplication.run(UrbtrackApplication.class, args);
	}

}
