package com.myorg.lsf.kafka.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("${lsf.kafka.admin.base-path:/lsf/kafka}")
public class LsfKafkaDlqController {

    private final LsfKafkaDlqService service;

    @GetMapping("/dlq/topics")
    public List<String> dlqTopics() {
        return service.listDlqTopics();
    }

    @GetMapping("/dlq/records")
    public List<LsfKafkaDlqRecordView> records(
            @RequestParam("topic") String topic,
            @RequestParam(name = "partition", required = false) Integer partition,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "beforeOffset", required = false) Long beforeOffset
    ) {
        return service.listRecords(topic, partition, limit, beforeOffset);
    }

    @GetMapping("/dlq/records/{topic}/{partition}/{offset}")
    public LsfKafkaDlqRecordView record(
            @PathVariable("topic") String topic,
            @PathVariable("partition") int partition,
            @PathVariable("offset") long offset
    ) {
        return service.getRecord(topic, partition, offset);
    }

    @PostMapping("/dlq/replay")
    public LsfKafkaReplayResult replay(@RequestBody LsfKafkaReplayRequest request) {
        return service.replay(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "BAD_REQUEST", "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> illegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "ILLEGAL_STATE", "message", ex.getMessage()));
    }
}
