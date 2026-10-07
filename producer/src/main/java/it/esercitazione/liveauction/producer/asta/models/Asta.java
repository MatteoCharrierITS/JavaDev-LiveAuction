package it.esercitazione.liveauction.producer.asta.models;

import it.esercitazione.liveauction.producer.auth.models.Utente;
import it.esercitazione.liveauction.producer.prodotto.models.Prodotto;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "aste")
@Getter
@Setter
public class Asta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prodotto_id", nullable = false)
    private Prodotto prodotto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Utente admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vincitore_id")
    private Utente vincitore;

    @Column(name = "stato", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Stato stato = Stato.PROGRAMMATA;

    @Column(name = "prezzo_iniziale", nullable = false, precision = 12, scale = 2)
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal prezzoIniziale;

    @Column(name = "incremento_minimo", nullable = false, precision = 12, scale = 2)
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal incrementoMinimo = new BigDecimal("1.00");

    @Column(name = "offerta_corrente", precision = 12, scale = 2)
    private BigDecimal offertaCorrente;

    @Column(name = "inizio_at", nullable = false)
    private Instant inizioAt;

    @Column(name = "fine_at", nullable = false)
    private Instant fineAt;

    @Column(name = "chiusa_at")
    private Instant chiusaAt;

    @Column(name = "sequence", nullable = false)
    private long sequence = 0;

    @Version
    @Column(name = "versione", nullable = false)
    private long versione = 0;

    @CreationTimestamp
    @Column(name = "data_creazione", nullable = false, updatable = false)
    private Instant dataCreazione;
}
