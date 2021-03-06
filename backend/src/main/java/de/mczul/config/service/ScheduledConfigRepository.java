package de.mczul.config.service;

import de.mczul.config.model.ScheduledConfigEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduledConfigRepository extends JpaRepository<ScheduledConfigEntry, Integer> {

    List<ScheduledConfigEntry> findByKey(String key);

    @Query("SELECT e1 " +
            "FROM ScheduledConfigEntry e1 " +
            "WHERE lower(e1.key) = lower(?1) " +
            "AND e1.validFrom <= current_timestamp " +
            "AND NOT EXISTS( " +
            "   SELECT 'x' " +
            "   FROM ScheduledConfigEntry e2 " +
            "   WHERE lower(e1.key) = lower(e2.key) " +
            "   AND e2.validFrom <= current_timestamp " +
            "   AND e2.validFrom > e1.validFrom " +
            ")")
    Optional<ScheduledConfigEntry> findCurrentByKey(String key);

    @Query("SELECT e1 " +
            "FROM ScheduledConfigEntry e1 " +
            "WHERE e1.validFrom <= current_timestamp " +
            "AND EXISTS( " +
            "   SELECT 'x' " +
            "   FROM ScheduledConfigEntry e2 " +
            "   WHERE e1.key = e2.key " +
            "   AND e2.validFrom <= current_timestamp " +
            "   AND e2.validFrom > e1.validFrom " +
            ")")
    List<ScheduledConfigEntry> findOutdated();

    @Query("SELECT e " +
            "FROM ScheduledConfigEntry e " +
            "WHERE e.key = ?1 " +
            "AND e.created < ?2 " +
            "ORDER BY e.created DESC")
    List<ScheduledConfigEntry> findHistory(String key, ZonedDateTime limit);

    @Query("SELECT e1 " +
            "FROM ScheduledConfigEntry e1 " +
            "WHERE NOT EXISTS (" +
            "   SELECT 'X' FROM ScheduledConfigEntry e2 WHERE e2.key = e1.key AND e2.created > e1.created" +
            ")")
    Page<ScheduledConfigEntry> findAllLatest(Pageable pageable);
}
