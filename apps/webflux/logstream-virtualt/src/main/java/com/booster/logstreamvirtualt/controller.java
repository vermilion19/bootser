package com.booster.logstreamvirtualt;

import com.booster.logstreamvirtualt.service.BlockingQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequiredArgsConstructor
public class controller {
    private final BlockingQueueService blockingQueueService;

    @PostMapping("/logs")
    public String receiveLog(@RequestBody String payload) {
        // 가상 스레드가 큐에 넣음 (BlockingQueue.offer는 짧은 시간의 Lock 발생)
        blockingQueueService.produce(payload);
        return "ok";
    }

}
