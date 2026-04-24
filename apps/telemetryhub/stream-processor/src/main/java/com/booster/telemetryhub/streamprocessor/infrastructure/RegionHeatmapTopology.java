package com.booster.telemetryhub.streamprocessor.infrastructure;

import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.streamprocessor.application.AggregationPlan;
import com.booster.telemetryhub.streamprocessor.application.RegionHeatmapProjectionWriter;
import com.booster.telemetryhub.streamprocessor.application.StreamTopologyPlanner;
import com.booster.telemetryhub.streamprocessor.config.StreamProcessorProperties;
import com.booster.telemetryhub.streamprocessor.domain.AggregationType;
import com.booster.telemetryhub.streamprocessor.domain.RawEventMessage;
import com.booster.telemetryhub.streamprocessor.domain.RegionHeatmapAggregate;
import com.booster.telemetryhub.streamprocessor.domain.RegionHeatmapKey;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegionHeatmapTopology {

    @Bean
    public KStream<String, RawEventMessage> regionHeatmapKStream(
            StreamsBuilder streamsBuilder,
            StreamTopologyPlanner streamTopologyPlanner,
            JsonSerdeFactory jsonSerdeFactory,
            RegionHeatmapProjection projection,
            RegionHeatmapProjectionWriter projectionWriter,
            RawEventDeduplicationSupport deduplicationSupport,
            StreamProcessorProperties properties,
            LateEventPolicySupport lateEventPolicySupport
    ) {
        AggregationPlan plan = streamTopologyPlanner.plan().aggregations().stream()
                .filter(aggregation -> aggregation.aggregationType() == AggregationType.REGION_HEATMAP)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("REGION_HEATMAP aggregation plan is missing"));

        KStream<String, RawEventMessage> sourceStream = streamsBuilder.stream(
                streamTopologyPlanner.plan().sourceTopic(),
                Consumed.with(Serdes.String(), jsonSerdeFactory.serde(RawEventMessage.class))
        );

        KStream<String, RawEventMessage> deduplicatedStream = deduplicationSupport.deduplicate(
                streamsBuilder,
                lateEventPolicySupport.retainWithinGrace(
                        sourceStream
                                .filter((key, event) -> event != null)
                                .filter((key, event) -> event.eventType() == EventType.TELEMETRY),
                        properties.getLateEventGrace()
                ),
                plan.stateStoreName() + "-event-id-dedup-store"
        );

        deduplicatedStream
                .map((key, event) -> org.apache.kafka.streams.KeyValue.pair(projection.project(event), event))
                .filter((projectedKey, event) -> projectedKey != null)
                .mapValues((projectedKey, event) -> RegionHeatmapAggregate.first(projectedKey))
                .groupByKey(Grouped.with(
                        jsonSerdeFactory.serde(RegionHeatmapKey.class),
                        jsonSerdeFactory.serde(RegionHeatmapAggregate.class)
                ))
                .reduce(
                        (aggregate, next) -> aggregate.increment(),
                        Materialized.with(
                                jsonSerdeFactory.serde(RegionHeatmapKey.class),
                                jsonSerdeFactory.serde(RegionHeatmapAggregate.class)
                        ).as(plan.stateStoreName())
                )
                .toStream()
                .foreach((heatmapKey, aggregate) -> projectionWriter.upsert(aggregate));

        return sourceStream;
    }
}
