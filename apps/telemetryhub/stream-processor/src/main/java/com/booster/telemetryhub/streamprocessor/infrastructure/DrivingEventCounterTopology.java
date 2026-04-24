package com.booster.telemetryhub.streamprocessor.infrastructure;

import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.streamprocessor.application.AggregationPlan;
import com.booster.telemetryhub.streamprocessor.application.DrivingEventCounterProjectionWriter;
import com.booster.telemetryhub.streamprocessor.application.StreamTopologyPlanner;
import com.booster.telemetryhub.streamprocessor.config.StreamProcessorProperties;
import com.booster.telemetryhub.streamprocessor.domain.AggregationType;
import com.booster.telemetryhub.streamprocessor.domain.DrivingEventCounterAggregate;
import com.booster.telemetryhub.streamprocessor.domain.DrivingEventCounterKey;
import com.booster.telemetryhub.streamprocessor.domain.RawEventMessage;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DrivingEventCounterTopology {

    @Bean
    public KStream<String, RawEventMessage> drivingEventCounterKStream(
            StreamsBuilder streamsBuilder,
            StreamTopologyPlanner streamTopologyPlanner,
            JsonSerdeFactory jsonSerdeFactory,
            DrivingEventCounterProjection projection,
            DrivingEventCounterProjectionWriter projectionWriter,
            RawEventDeduplicationSupport deduplicationSupport,
            StreamProcessorProperties properties,
            LateEventPolicySupport lateEventPolicySupport
    ) {
        AggregationPlan plan = streamTopologyPlanner.plan().aggregations().stream()
                .filter(aggregation -> aggregation.aggregationType() == AggregationType.DRIVING_EVENT_COUNTER)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("DRIVING_EVENT_COUNTER aggregation plan is missing"));

        KStream<String, RawEventMessage> sourceStream = streamsBuilder.stream(
                streamTopologyPlanner.plan().sourceTopic(),
                Consumed.with(Serdes.String(), jsonSerdeFactory.serde(RawEventMessage.class))
        );

        KStream<String, RawEventMessage> deduplicatedStream = deduplicationSupport.deduplicate(
                streamsBuilder,
                lateEventPolicySupport.retainWithinGrace(
                        sourceStream
                                .filter((key, event) -> event != null)
                                .filter((key, event) -> event.eventType() == EventType.DRIVING_EVENT),
                        properties.getLateEventGrace()
                ),
                plan.stateStoreName() + "-event-id-dedup-store"
        );

        deduplicatedStream
                .map((key, event) -> org.apache.kafka.streams.KeyValue.pair(projection.project(event), event))
                .filter((projectedKey, event) -> projectedKey != null)
                .mapValues((projectedKey, event) -> DrivingEventCounterAggregate.first(projectedKey))
                .groupByKey(Grouped.with(
                        jsonSerdeFactory.serde(DrivingEventCounterKey.class),
                        jsonSerdeFactory.serde(DrivingEventCounterAggregate.class)
                ))
                .reduce(
                        (aggregate, next) -> aggregate.increment(),
                        Materialized.with(
                                jsonSerdeFactory.serde(DrivingEventCounterKey.class),
                                jsonSerdeFactory.serde(DrivingEventCounterAggregate.class)
                        ).as(plan.stateStoreName())
                )
                .toStream()
                .foreach((bucketKey, aggregate) -> projectionWriter.upsert(aggregate));

        return sourceStream;
    }
}
