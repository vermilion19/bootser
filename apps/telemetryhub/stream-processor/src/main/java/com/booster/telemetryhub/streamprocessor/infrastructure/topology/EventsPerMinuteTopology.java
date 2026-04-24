package com.booster.telemetryhub.streamprocessor.infrastructure.topology;

import com.booster.telemetryhub.streamprocessor.application.plan.AggregationPlan;
import com.booster.telemetryhub.streamprocessor.application.plan.StreamTopologyPlanner;
import com.booster.telemetryhub.streamprocessor.application.projection.EventsPerMinuteProjectionWriter;
import com.booster.telemetryhub.streamprocessor.config.StreamProcessorProperties;
import com.booster.telemetryhub.streamprocessor.domain.AggregationType;
import com.booster.telemetryhub.streamprocessor.domain.EventsPerMinuteAggregate;
import com.booster.telemetryhub.streamprocessor.domain.EventsPerMinuteKey;
import com.booster.telemetryhub.streamprocessor.domain.RawEventMessage;
import com.booster.telemetryhub.streamprocessor.infrastructure.serde.JsonSerdeFactory;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventsPerMinuteTopology {

    @Bean
    public KStream<String, RawEventMessage> eventsPerMinuteKStream(
            StreamsBuilder streamsBuilder,
            StreamTopologyPlanner streamTopologyPlanner,
            JsonSerdeFactory jsonSerdeFactory,
            EventsPerMinuteProjectionWriter projectionWriter,
            RawEventDeduplicationSupport deduplicationSupport,
            StreamProcessorProperties properties,
            LateEventPolicySupport lateEventPolicySupport
    ) {
        AggregationPlan plan = streamTopologyPlanner.plan().aggregations().stream()
                .filter(aggregation -> aggregation.aggregationType() == AggregationType.EVENTS_PER_MINUTE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("EVENTS_PER_MINUTE aggregation plan is missing"));

        KStream<String, RawEventMessage> sourceStream = streamsBuilder.stream(
                streamTopologyPlanner.plan().sourceTopic(),
                Consumed.with(Serdes.String(), jsonSerdeFactory.serde(RawEventMessage.class))
        );

        KStream<String, RawEventMessage> deduplicatedStream = deduplicationSupport.deduplicate(
                streamsBuilder,
                lateEventPolicySupport.retainWithinGrace(
                        sourceStream.filter((key, event) -> event != null),
                        properties.getLateEventGrace()
                ),
                plan.stateStoreName() + "-event-id-dedup-store"
        );

        deduplicatedStream
                .selectKey((key, event) -> EventsPerMinuteKey.from(event))
                .mapValues((bucketKey, event) -> EventsPerMinuteAggregate.first(bucketKey))
                .groupByKey(Grouped.with(
                        jsonSerdeFactory.serde(EventsPerMinuteKey.class),
                        jsonSerdeFactory.serde(EventsPerMinuteAggregate.class)
                ))
                .reduce(
                        (aggregate, next) -> aggregate.increment(),
                        Materialized.with(
                                jsonSerdeFactory.serde(EventsPerMinuteKey.class),
                                jsonSerdeFactory.serde(EventsPerMinuteAggregate.class)
                        ).as(plan.stateStoreName())
                )
                .toStream()
                .foreach((bucketKey, aggregate) -> projectionWriter.upsert(aggregate));

        return sourceStream;
    }
}
