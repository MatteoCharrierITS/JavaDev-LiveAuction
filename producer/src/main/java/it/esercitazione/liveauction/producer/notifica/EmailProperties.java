package it.esercitazione.liveauction.producer.notifica;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties("app.notifiche.email")
public class EmailProperties {
    private boolean enabled = false;
    @Email private String from = "";
    @Min(1) private long retrySeconds = 60;
    @Min(1) @Max(100) private int batchSize = 20;
    @Min(1000) private long intervalMs = 30000;

    @AssertTrue(message = "Configurare un mittente quando le email sono abilitate")
    public boolean isMittenteConfigurato() {
        return !enabled || (from != null && !from.isBlank());
    }
}
