package it.esercitazione.liveauction.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LiveAuctionConsumerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LiveAuctionConsumerApplication.class, args);
	}

}
