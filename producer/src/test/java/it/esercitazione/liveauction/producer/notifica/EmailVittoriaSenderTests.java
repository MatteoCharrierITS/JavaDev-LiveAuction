package it.esercitazione.liveauction.producer.notifica;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailVittoriaSenderTests {
    @Test
    void formatsSummaryWithItalianCreditsAndRomeTime() {
        var mail = mock(JavaMailSender.class);
        var beans = new StaticListableBeanFactory();
        beans.addBean("mail", mail);
        var properties = new EmailProperties();
        properties.setFrom("aste@example.test");
        var sender = new EmailVittoriaSender(beans.getBeanProvider(JavaMailSender.class), properties);
        sender.invia(new EmailVittoriaSender.Vittoria(42, "winner@example.test", "Laptop",
                new BigDecimal("1234.50"), Instant.parse("2026-10-08T12:00:00Z")));
        var captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captured.capture());
        assertThat(captured.getValue().getTo()).containsExactly("winner@example.test");
        assertThat(captured.getValue().getFrom()).isEqualTo("aste@example.test");
        assertThat(captured.getValue().getText()).contains("#42", "Laptop", "1.234,50 CRD", "08/10/2026 14:00:00");
    }

    @Test
    void validatesCredentialsPortAndConflictingTlsBeforeSending() {
        var mail = new JavaMailSenderImpl();
        mail.setHost("smtp.example.invalid");
        var beans = new StaticListableBeanFactory();
        beans.addBean("mail", mail);
        var properties = new EmailProperties();
        properties.setEnabled(true);
        properties.setFrom("aste@example.test");
        var sender = new EmailVittoriaSender(beans.getBeanProvider(JavaMailSender.class), properties);
        mail.getJavaMailProperties().setProperty("mail.smtp.auth", "true");
        assertThatThrownBy(sender::verificaConfigurazione).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP_USERNAME");
        mail.setUsername("fixture");
        mail.setPassword("fixture-not-a-real-secret");
        mail.setPort(0);
        assertThatThrownBy(sender::verificaConfigurazione).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP_PORT");
        mail.setPort(587);
        assertThatCode(sender::verificaConfigurazione).doesNotThrowAnyException();
        mail.getJavaMailProperties().setProperty("mail.smtp.ssl.enable", "true");
        mail.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", "true");
        assertThatThrownBy(sender::verificaConfigurazione).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP_SSL");
        mail.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", "false");
        assertThatCode(sender::verificaConfigurazione).doesNotThrowAnyException();
    }

    @Test
    void disabledSenderNeedsNoSmtpButEnabledSenderFailsFastWithoutHost() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("mail", new JavaMailSenderImpl());
        var properties = new EmailProperties();
        var sender = new EmailVittoriaSender(beans.getBeanProvider(JavaMailSender.class), properties);
        assertThatCode(sender::verificaConfigurazione).doesNotThrowAnyException();
        properties.setEnabled(true);
        assertThatThrownBy(sender::verificaConfigurazione).isInstanceOf(IllegalStateException.class);
    }
}
