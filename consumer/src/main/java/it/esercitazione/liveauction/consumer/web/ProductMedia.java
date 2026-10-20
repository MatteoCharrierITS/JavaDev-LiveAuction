package it.esercitazione.liveauction.consumer.web;

import java.text.Normalizer;
import java.util.Locale;

/** Immagini illustrative locali, non fotografie o dati commerciali del Producer. */
public class ProductMedia {
    public String image(String name) {
        String key = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String file;
        if (key.contains("cuff") || key.contains("headphone") || key.contains("audio")) file = "headphones.webp";
        else if (key.contains("tastier") || key.contains("keyboard")) file = "keyboard.svg";
        else if (key.contains("mouse")) file = "mouse.svg";
        else if (key.contains("notebook") || key.contains("laptop") || key.contains("informatica")) file = "laptop.webp";
        else if (key.contains("monitor") || key.contains("schermo") || key.contains("display")) file = "monitor.svg";
        else if (key.contains("lamp")) file = "lamp.webp";
        else if (key.contains("zaino") || key.contains("backpack") || key.contains("tempo-libero")) file = "backpack.webp";
        else if (key.contains("speaker") || key.contains("altoparlante")) file = "speaker.svg";
        else file = "generic.svg";
        return "/images/products/" + file;
    }
}
