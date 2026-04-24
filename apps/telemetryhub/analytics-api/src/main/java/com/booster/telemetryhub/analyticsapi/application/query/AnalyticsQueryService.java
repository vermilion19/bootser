package com.booster.telemetryhub.analyticsapi.application.query;

import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.contracts.drivingevent.DrivingEventType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class AnalyticsQueryService {

    private final EntityManager entityManager;

    public AnalyticsQueryService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<LatestDeviceView> getLatestDevices(int limit) {
        Query query = entityManager.createNativeQuery("""
                select device_id,
                       last_event_id,
                       last_event_type,
                       last_event_time,
                       last_ingest_time,
                       source_topic
                from telemetryhub_device_last_seen
                order by last_event_time desc
                limit :limit
                """);
        query.setParameter("limit", limit);
        return mapLatestDevices(query.getResultList());
    }

    public LatestDeviceView getLatestDevice(String deviceId) {
        Query query = entityManager.createNativeQuery("""
                select device_id,
                       last_event_id,
                       last_event_type,
                       last_event_time,
                       last_ingest_time,
                       source_topic
                from telemetryhub_device_last_seen
                where device_id = :deviceId
                """);
        query.setParameter("deviceId", deviceId);
        List<LatestDeviceView> results = mapLatestDevices(query.getResultList());
        return results.isEmpty() ? null : results.getFirst();
    }

    public List<EventsPerMinuteView> getEventsPerMinute(
            Instant from,
            Instant to,
            EventType eventType
    ) {
        StringBuilder sql = new StringBuilder("""
                select event_type,
                       minute_bucket_start,
                       event_count
                from telemetryhub_events_per_minute
                where minute_bucket_start between :from and :to
                """);

        if (eventType != null) {
            sql.append(" and event_type = :eventType");
        }

        sql.append(" order by minute_bucket_start asc, event_type asc");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("from", Timestamp.from(from));
        query.setParameter("to", Timestamp.from(to));
        if (eventType != null) {
            query.setParameter("eventType", eventType.name());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream()
                .map(row -> new EventsPerMinuteView(
                        EventType.valueOf((String) row[0]),
                        toInstant(row[1]),
                        ((Number) row[2]).longValue()
                ))
                .toList();
    }

    public List<DrivingEventCounterView> getDrivingEventCounters(
            Instant from,
            Instant to,
            String deviceId,
            DrivingEventType drivingEventType
    ) {
        StringBuilder sql = new StringBuilder("""
                select device_id,
                       driving_event_type,
                       minute_bucket_start,
                       event_count
                from telemetryhub_driving_event_counter
                where minute_bucket_start between :from and :to
                """);

        if (deviceId != null && !deviceId.isBlank()) {
            sql.append(" and device_id = :deviceId");
        }
        if (drivingEventType != null) {
            sql.append(" and driving_event_type = :drivingEventType");
        }

        sql.append(" order by minute_bucket_start asc, device_id asc");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("from", Timestamp.from(from));
        query.setParameter("to", Timestamp.from(to));
        if (deviceId != null && !deviceId.isBlank()) {
            query.setParameter("deviceId", deviceId);
        }
        if (drivingEventType != null) {
            query.setParameter("drivingEventType", drivingEventType.name());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream()
                .map(row -> new DrivingEventCounterView(
                        (String) row[0],
                        DrivingEventType.valueOf((String) row[1]),
                        toInstant(row[2]),
                        ((Number) row[3]).longValue()
                ))
                .toList();
    }

    public List<RegionHeatmapView> getRegionHeatmap(
            Instant from,
            Instant to,
            Double minLat,
            Double maxLat,
            Double minLon,
            Double maxLon
    ) {
        StringBuilder sql = new StringBuilder("""
                select grid_lat,
                       grid_lon,
                       minute_bucket_start,
                       sum(event_count) as total_event_count
                from telemetryhub_region_heatmap
                where minute_bucket_start between :from and :to
                """);

        if (minLat != null) {
            sql.append(" and grid_lat >= :minLat");
        }
        if (maxLat != null) {
            sql.append(" and grid_lat <= :maxLat");
        }
        if (minLon != null) {
            sql.append(" and grid_lon >= :minLon");
        }
        if (maxLon != null) {
            sql.append(" and grid_lon <= :maxLon");
        }

        sql.append("""
                 group by grid_lat, grid_lon, minute_bucket_start
                 order by minute_bucket_start asc, total_event_count desc
                """);

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("from", Timestamp.from(from));
        query.setParameter("to", Timestamp.from(to));
        if (minLat != null) {
            query.setParameter("minLat", minLat);
        }
        if (maxLat != null) {
            query.setParameter("maxLat", maxLat);
        }
        if (minLon != null) {
            query.setParameter("minLon", minLon);
        }
        if (maxLon != null) {
            query.setParameter("maxLon", maxLon);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream()
                .map(row -> new RegionHeatmapView(
                        ((Number) row[0]).doubleValue(),
                        ((Number) row[1]).doubleValue(),
                        toInstant(row[2]),
                        ((Number) row[3]).longValue()
                ))
                .toList();
    }

    private List<LatestDeviceView> mapLatestDevices(List<?> rows) {
        return rows.stream()
                .map(Object[].class::cast)
                .map(row -> new LatestDeviceView(
                        (String) row[0],
                        (String) row[1],
                        EventType.valueOf((String) row[2]),
                        toInstant(row[3]),
                        toInstant(row[4]),
                        (String) row[5]
                ))
                .toList();
    }

    private Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        throw new IllegalStateException("Unsupported time value: " + value);
    }
}
