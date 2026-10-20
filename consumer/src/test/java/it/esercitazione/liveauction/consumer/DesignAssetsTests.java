package it.esercitazione.liveauction.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DesignAssetsTests {
    @Autowired MockMvc mvc;

    @Test void illustrazioniLocaliSonoPubblicheAncheSenzaSessione() throws Exception {
        mvc.perform(get("/images/products/headphones.webp")).andExpect(status().isOk())
                .andExpect(content().contentType("image/webp"));
        mvc.perform(get("/images/products/keyboard.svg")).andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"));
        mvc.perform(get("/images/products/generic.svg")).andExpect(status().isOk());
    }

    @Test void stileEScriptSonoServitiLocalmenteConVersione() throws Exception {
        mvc.perform(get("/css/site.css").param("v", "20261020-design")).andExpect(status().isOk())
                .andExpect(content().string(containsString("prefers-reduced-motion")));
        mvc.perform(get("/js/site.js").param("v", "20261020-design")).andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-expanded")));
    }
}
