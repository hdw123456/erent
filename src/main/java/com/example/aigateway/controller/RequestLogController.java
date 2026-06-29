package com.example.aigateway.controller;

import com.example.aigateway.dto.response.RequestLogPageResponse;
import com.example.aigateway.service.CurrentUserService;
import com.example.aigateway.service.RequestLogService;
import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/request-logs")
public class RequestLogController {
    private final RequestLogService requestLogService;
    private final CurrentUserService currentUserService;

    public RequestLogController(RequestLogService requestLogService, CurrentUserService currentUserService) {
        this.requestLogService = requestLogService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public RequestLogPageResponse listRequestLogs(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long modelId,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime
    ) {
        return requestLogService.searchUserRequestLogs(
                currentUserService.getCurrentUserId(),
                page,
                size,
                modelId,
                statusCode,
                startTime,
                endTime
        );
    }
}
