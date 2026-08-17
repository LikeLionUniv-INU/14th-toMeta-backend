package com.likelion.tometa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:tometa-context;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=test",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
		"spring.flyway.locations=classpath:db/migration/h2",
		"app.storage.s3.bucket=test-bucket"
})
class TometaApplicationTests {

	@Test
	void contextLoads() {
	}

}
