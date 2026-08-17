package com.likelion.tometa;

import com.likelion.tometa.global.config.AnonymousSessionProperties;
import com.likelion.tometa.global.config.openai.OpenAiProperties;
import com.likelion.tometa.global.config.s3.S3StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
		AnonymousSessionProperties.class,
		S3StorageProperties.class,
		OpenAiProperties.class
})
public class TometaApplication {

	public static void main(String[] args) {
		SpringApplication.run(TometaApplication.class, args);
	}

}
