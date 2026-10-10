package it.esercitazione.liveauction.producer.notifica;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class EmailVittoriaSender {
    private final ObjectProvider<JavaMailSender> sender;
    private final EmailProperties properties;

    @PostConstruct
    void verificaConfigurazione() {
        JavaMailSender mail = sender.getIfAvailable();
        if (properties.isEnabled() && (mail == null || (mail instanceof JavaMailSenderImpl smtp
                && (smtp.getHost() == null || smtp.getHost().isBlank())))) {
            throw new IllegalStateException("Email abilitate: configurare SMTP_HOST e il mittente");
        }
        if (properties.isEnabled() && mail instanceof JavaMailSenderImpl smtp) {
            if (smtp.getPort() != -1 && (smtp.getPort() < 1 || smtp.getPort() > 65535)) {
                throw new IllegalStateException("Email abilitate: SMTP_PORT deve essere tra 1 e 65535");
            }
            var config = smtp.getJavaMailProperties();
            if (Boolean.parseBoolean(config.getProperty("mail.smtp.auth"))
                    && (smtp.getUsername() == null || smtp.getUsername().isBlank()
                    || smtp.getPassword() == null || smtp.getPassword().isBlank())) {
                throw new IllegalStateException("Email abilitate: configurare SMTP_USERNAME e SMTP_PASSWORD oppure SMTP_AUTH=false");
            }
            if (Boolean.parseBoolean(config.getProperty("mail.smtp.ssl.enable"))
                    && (Boolean.parseBoolean(config.getProperty("mail.smtp.starttls.enable"))
                    || Boolean.parseBoolean(config.getProperty("mail.smtp.starttls.required")))) {
                throw new IllegalStateException("SMTP_SSL richiede SMTP_STARTTLS=false e SMTP_STARTTLS_REQUIRED=false");
            }
        }
    }

    public void invia(Vittoria vittoria) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFrom());
        message.setTo(vittoria.email());
        message.setSubject("LiveAuction — vittoria asta #" + vittoria.astaId());
        String data = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss z", Locale.ITALY)
                .withZone(ZoneId.of("Europe/Rome")).format(vittoria.chiusaAt());
        var numero = NumberFormat.getNumberInstance(Locale.ITALY);
        numero.setMinimumFractionDigits(2);
        numero.setMaximumFractionDigits(2);
        message.setText("Hai vinto l'asta #" + vittoria.astaId() + ".\nProdotto: " + vittoria.prodotto()
                + "\nImporto vincente: " + numero.format(vittoria.importo()) + " CRD"
                + "\nData di conclusione: " + data + "\n");
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null) throw new IllegalStateException("SMTP non configurato");
        mail.send(message);
    }

    public record Vittoria(long astaId, String email, String prodotto, BigDecimal importo, Instant chiusaAt) {}
}
