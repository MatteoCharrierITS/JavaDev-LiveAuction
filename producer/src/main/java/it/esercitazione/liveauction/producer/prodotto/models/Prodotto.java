package it.esercitazione.liveauction.producer.prodotto.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "prodotti")
@Getter @Setter
public class Prodotto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(name = "sku", unique = true, nullable = false, length = 30)
    private String sku;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "descrizione", columnDefinition = "TEXT")
    private String descrizione;

    @Column(name = "prezzo_fisso", precision = 12, scale = 2)
    private BigDecimal prezzoFisso;

    @Column(name = "astabile", nullable = false)
    private boolean astabile = false;

    @Column(name = "quantita_disponibile", nullable = false)
    private int quantitaDisponibile = 0;

    @Column(name = "quantita_bloccata", nullable = false)
    private int quantitaBloccata = 0;

    @Column(name = "attivo", nullable = false)
    private boolean attivo = true;

    @Version
    @Column(name = "versione", nullable = false)
    private long versione;

    @Column(name = "data_creazione", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant dataCreazione;

}
