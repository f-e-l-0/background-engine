package com.example.engine.schedule.repository;

import com.example.engine.schedule.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    /**
     * FOR UPDATE SKIP LOCKED: when several engine instances poll at once, each locks a different
     * set of due rows. No schedule fires twice, and no instance waits on another's lock.
     */
    @Query(value = """
            SELECT * FROM schedules
            WHERE enabled = TRUE AND next_run_at <= :now
            ORDER BY next_run_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Schedule> findDueForUpdate(@Param("now") Instant now, @Param("limit") int limit);
}
