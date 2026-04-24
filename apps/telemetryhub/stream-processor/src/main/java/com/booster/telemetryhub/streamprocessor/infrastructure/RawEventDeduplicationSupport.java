package com.booster.telemetryhub.streamprocessor.infrastructure;

import com.booster.telemetryhub.streamprocessor.domain.RawEventMessage;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorSupplier;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.stereotype.Component;

@Component
public class RawEventDeduplicationSupport {

    public KStream<String, RawEventMessage> deduplicate(
            StreamsBuilder streamsBuilder,
            KStream<String, RawEventMessage> sourceStream,
            String storeName
    ) {
        registerStore(streamsBuilder, storeName);
        return sourceStream.processValues(() -> new EventIdDeduplicationProcessor(storeName), storeName)
                .filter((key, event) -> event != null);
    }

    private void registerStore(StreamsBuilder streamsBuilder, String storeName) {
        StoreBuilder<KeyValueStore<String, Long>> storeBuilder = Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(storeName),
                Serdes.String(),
                Serdes.Long()
        );
        streamsBuilder.addStateStore(storeBuilder);
    }

    private static class EventIdDeduplicationProcessor implements FixedKeyProcessor<String, RawEventMessage, RawEventMessage> {

        private final String storeName;
        private FixedKeyProcessorContext<String, RawEventMessage> context;
        private KeyValueStore<String, Long> store;

        private EventIdDeduplicationProcessor(String storeName) {
            this.storeName = storeName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void init(FixedKeyProcessorContext<String, RawEventMessage> context) {
            this.context = context;
            this.store = (KeyValueStore<String, Long>) context.getStateStore(storeName);
        }

        @Override
        public void process(FixedKeyRecord<String, RawEventMessage> record) {
            RawEventMessage value = record.value();
            if (value == null || value.eventId() == null || value.eventId().isBlank()) {
                context.forward(record);
                return;
            }

            if (store.get(value.eventId()) != null) {
                context.forward(record.withValue(null));
                return;
            }

            long seenAt = value.ingestTime() != null ? value.ingestTime().toEpochMilli() : System.currentTimeMillis();
            store.put(value.eventId(), seenAt);
            context.forward(record);
        }

        @Override
        public void close() {
        }
    }
}
