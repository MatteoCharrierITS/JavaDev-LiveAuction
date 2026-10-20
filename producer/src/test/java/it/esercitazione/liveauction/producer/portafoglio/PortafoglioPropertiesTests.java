package it.esercitazione.liveauction.producer.portafoglio;

import it.esercitazione.liveauction.producer.portafoglio.config.PortafoglioProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

class PortafoglioPropertiesTests {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PortafoglioProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test void configuredDecimalIsBoundWithoutRounding() {
        runner.withPropertyValues("app.portafoglio.saldo-iniziale=4321.25").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PortafoglioProperties.class).saldoIniziale()).isEqualByComparingTo("4321.25");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.001", "10000000000", "abc", ""})
    void invalidInitialCreditStopsStartup(String value) {
        runner.withPropertyValues("app.portafoglio.saldo-iniziale=" + value)
                .run(context -> assertThat(context).hasFailed());
    }
}
