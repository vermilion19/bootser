package com.booster.telemetryhub.streamprocessor.infrastructure.topology;

import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.streamprocessor.application.plan.AggregationPlan;
import com.booster.telemetryhub.streamprocessor.application.plan.StreamTopologyPlanner;
import com.booster.telemetryhub.streamprocessor.application.projection.DeviceLastSeenProjectionWriter;
import com.booster.telemetryhub.streamprocessor.config.StreamProcessorProperties;
import com.booster.telemetryhub.streamprocessor.domain.AggregationType;
import com.booster.telemetryhub.streamprocessor.domain.DeviceLastSeenAggregate;
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
public class DeviceLastSeenTopology {

    @Bean
    public KStream<String, RawEventMessage> deviceLastSeenKStream(
            StreamsBuilder streamsBuilder,
            StreamTopologyPlanner streamTopologyPlanner,
            JsonSerdeFactory jsonSerdeFactory,
            DeviceLastSeenProjectionWriter projectionWriter,
            StreamProcessorProperties properties,
            LateEventPolicySupport lateEventPolicySupport
    ) {
        AggregationPlan plan = streamTopologyPlanner.plan().aggregations().stream()
                .filter(aggregation -> aggregation.aggregationType() == AggregationType.DEVICE_LAST_SEEN)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("DEVICE_LAST_SEEN aggregation plan is missing"));

        KStream<String, RawEventMessage> sourceStream = streamsBuilder.stream(
                streamTopologyPlanner.plan().sourceTopic(),
                Consumed.with(Serdes.String(), jsonSerdeFactory.serde(RawEventMessage.class))
        );

        lateEventPolicySupport.retainWithinGrace(
                        sourceStream.filter((key, event) -> event != null),
                        properties.getLateEventGrace()
                )
                .filter((key, event) -> event != null)
                .filter((key, event) -> isSupportedForLastSeen(event.eventType()))
                .selectKey((key, event) -> event.deviceId())
                .mapValues(DeviceLastSeenAggregate::from)
                .groupByKey(Grouped.with(Serdes.String(), jsonSerdeFactory.serde(DeviceLastSeenAggregate.class)))
                .reduce(
                        DeviceLastSeenAggregate::merge,
                        Materialized.with(Serdes.String(), jsonSerdeFactory.serde(DeviceLastSeenAggregate.class))
                                .as(plan.stateStoreName())
                )
                .toStream()
                .foreach((deviceId, aggregate) -> projectionWriter.upsert(aggregate));

        return sourceStream;
    }

    private boolean isSupportedForLastSeen(EventType eventType) {
        return eventType == EventType.TELEMETRY
                || eventType == EventType.DEVICE_HEALTH
                || eventType == EventType.DRIVING_EVENT;
    }
}
