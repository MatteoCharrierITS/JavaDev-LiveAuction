package it.esercitazione.liveauction.producer.acquisto;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data @Component @Validated @ConfigurationProperties("app.acquisti")
public class AcquistiProperties {
    @Min(0) private long adminId;
}
