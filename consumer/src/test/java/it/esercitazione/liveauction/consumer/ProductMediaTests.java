package it.esercitazione.liveauction.consumer;

import it.esercitazione.liveauction.consumer.web.ProductMedia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class ProductMediaTests {
    private final ProductMedia media = new ProductMedia();

    @ParameterizedTest
    @CsvSource({"CUFFIE Bluetooth,headphones.webp", "Keyboard RGB,keyboard.svg", "Mouse wireless,mouse.svg",
            "Notebook Studio,laptop.webp", "Monitor QHD,monitor.svg", "Lampada LED,lamp.webp",
            "Zaino urbano,backpack.webp", "Speaker esaurito,speaker.svg"})
    void sceglieSoloAssetLocaliPerTipo(String name, String file) {
        assertThat(media.image(name)).isEqualTo("/images/products/" + file);
    }

    @Test
    void datiAssentiOSconosciutiUsanoFallbackSenzaCostruireUrlDalNome() {
        assertThat(media.image(null)).isEqualTo("/images/products/generic.svg");
        assertThat(media.image("https://example.invalid/../../private?token=secret"))
                .isEqualTo("/images/products/generic.svg");
    }
}
