package it.esercitazione.liveauction.producer.acquisto;

import it.esercitazione.liveauction.producer.portafoglio.repos.PortafoglioRepository;
import it.esercitazione.liveauction.producer.portafoglio.model.TipoMovimento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service @Validated @RequiredArgsConstructor
public class AcquistoService {
    private final JdbcTemplate jdbc;
    private final PortafoglioRepository portafogli;
    private final AcquistiProperties config;

    @Transactional
    @PreAuthorize("hasRole('USER') and authentication.token.subject == #p0.toString()")
    public AcquistoResponse compra(@Positive long utenteId, @Positive long prodottoId,
                                   @NotNull @Valid AcquistoRequest request) {
        // Stesso ordine del dominio aste: utente, portafogli per ID, prodotto, inventario.
        portafogli.verificaUtenteAttivo(utenteId,true);
        var precedente=precedente(utenteId,prodottoId,request);
        if(precedente!=null) return precedente;
        long admin=config.getAdminId();
        if(admin==utenteId || jdbc.queryForObject("SELECT count(*) FROM utenti WHERE id=? AND ruolo='ADMIN' AND attivo",
                Integer.class,admin)!=1) {
            throw errore(HttpStatus.SERVICE_UNAVAILABLE,"ACQUISTI_NON_CONFIGURATI","Configurare SHOP_ADMIN_ID con un ADMIN attivo");
        }
        var saldi=portafogli.bloccaPortafogli(List.of(utenteId,admin));
        precedente=precedente(utenteId,prodottoId,request);
        if(precedente!=null) return precedente;
        var prodotti=jdbc.query("""
                SELECT p.prezzo_fisso, p.quantita_disponibile, p.attivo, c.attiva
                FROM prodotti p JOIN categorie c ON c.id=p.categoria_id WHERE p.id=? FOR UPDATE OF p
                """,(rs,row)->new Prodotto(rs.getBigDecimal("prezzo_fisso"),rs.getInt("quantita_disponibile"),
                rs.getBoolean("attivo") && rs.getBoolean("attiva")),prodottoId);
        if(prodotti.isEmpty() || !prodotti.getFirst().visibile()) {
            throw errore(HttpStatus.NOT_FOUND,"RISORSA_NON_TROVATA","Prodotto non disponibile nel catalogo");
        }
        var prodotto=prodotti.getFirst();
        if(prodotto.prezzo()==null) throw errore(HttpStatus.CONFLICT,"PRODOTTO_NON_ACQUISTABILE","Prezzo fisso non disponibile");
        if(prodotto.disponibile()<request.quantita()) throw errore(HttpStatus.CONFLICT,"PRODOTTO_NON_DISPONIBILE","Quantità disponibile insufficiente");
        BigDecimal totale=prodotto.prezzo().multiply(BigDecimal.valueOf(request.quantita()));
        if(totale.compareTo(new BigDecimal("9999999999.99"))>0) throw errore(HttpStatus.CONFLICT,"IMPORTO_ACQUISTO_ECCESSIVO","Totale oltre il limite consentito");
        if(saldi.get(utenteId).disponibile().compareTo(totale)<0) throw errore(HttpStatus.CONFLICT,"SALDO_INSUFFICIENTE","Saldo disponibile insufficiente");
        Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        int inseriti=jdbc.update("""
                INSERT INTO acquisti_fissi(id,utente_id,prodotto_id,admin_id,quantita,prezzo_unitario,totale,acquistato_at)
                VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING
                """,request.clientPurchaseId(),utenteId,prodottoId,admin,request.quantita(),prodotto.prezzo(),totale,Timestamp.from(now));
        if(inseriti==0) return Objects.requireNonNull(precedente(utenteId,prodottoId,request));
        portafogli.movimenta(saldi.get(utenteId),null,TipoMovimento.ACQUISTO_FISSO,totale,totale.negate(),BigDecimal.ZERO,now);
        portafogli.movimenta(saldi.get(admin),null,TipoMovimento.INCASSO_FISSO,totale,totale,BigDecimal.ZERO,now);
        jdbc.update("UPDATE prodotti SET quantita_disponibile=quantita_disponibile-?, versione=versione+1 WHERE id=?",request.quantita(),prodottoId);
        jdbc.update("""
                INSERT INTO inventario_utenti(utente_id,prodotto_id,quantita) VALUES (?,?,?)
                ON CONFLICT (utente_id,prodotto_id) DO UPDATE
                SET quantita=inventario_utenti.quantita+EXCLUDED.quantita, versione=inventario_utenti.versione+1
                """,utenteId,prodottoId,request.quantita());
        return new AcquistoResponse(request.clientPurchaseId(),prodottoId,request.quantita(),prodotto.prezzo(),totale,"CRD",now,false);
    }

    private AcquistoResponse precedente(long utente,long prodotto,AcquistoRequest request) {
        var righe=jdbc.query("SELECT * FROM acquisti_fissi WHERE id=?",(rs,row)-> {
            if(rs.getLong("utente_id")!=utente || rs.getLong("prodotto_id")!=prodotto || rs.getInt("quantita")!=request.quantita()) {
                throw errore(HttpStatus.CONFLICT,"CLIENT_PURCHASE_ID_GIA_UTILIZZATO","UUID già usato con dati diversi");
            }
            return new AcquistoResponse(request.clientPurchaseId(),prodotto,request.quantita(),rs.getBigDecimal("prezzo_unitario"),
                    rs.getBigDecimal("totale"),"CRD",rs.getTimestamp("acquistato_at").toInstant(),true);
        },request.clientPurchaseId());
        return righe.isEmpty()?null:righe.getFirst();
    }
    private static AcquistoException errore(HttpStatus status,String code,String detail) {return new AcquistoException(status,code,detail);}
    private record Prodotto(BigDecimal prezzo,int disponibile,boolean visibile) {}
}
