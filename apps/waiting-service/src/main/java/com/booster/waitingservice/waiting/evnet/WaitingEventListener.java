package com.booster.waitingservice.waiting.evnet;

import com.booster.core.web.event.WaitingEvent;
import com.booster.waitingservice.waiting.application.WaitingEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WaitingEventListener {

    private final WaitingEventProducer eventProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(WaitingEvent event) {
        eventProducer.send(event);
    }
}
