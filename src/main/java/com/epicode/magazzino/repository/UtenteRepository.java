package com.epicode.magazzino.repository;

import com.epicode.magazzino.model.Utente;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accesso agli account e blocco pessimista durante i tentativi di login. */
public interface UtenteRepository extends JpaRepository<Utente, Long> {

    Optional<Utente> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRuolo(int ruolo);

    List<Utente> findAllByOrderByUsernameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Utente u where u.username = :username")
    Optional<Utente> findByUsernameForUpdate(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Utente u where u.ruolo = :ruolo order by u.id")
    List<Utente> findAllByRuoloForUpdate(@Param("ruolo") int ruolo);
}
