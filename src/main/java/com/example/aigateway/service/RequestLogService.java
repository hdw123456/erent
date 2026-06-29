package com.example.aigateway.service;

import com.example.aigateway.dto.RequestLogQuery;
import com.example.aigateway.dto.response.RequestLogPageResponse;
import com.example.aigateway.dto.response.RequestLogResponse;
import com.example.aigateway.entity.RequestLog;
import com.example.aigateway.exception.BusinessException;
import com.example.aigateway.mapper.RequestLogMapper;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RequestLogService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final RequestLogMapper requestLogMapper;

    public RequestLogService(RequestLogMapper requestLogMapper) {
        this.requestLogMapper = requestLogMapper;
    }

    public RequestLogPageResponse searchUserRequestLogs(
            Long userId,
            Integer page,
            Integer size,
            Long modelId,
            Integer statusCode,
            Date startTime,
            Date endTime
    ) {
        if (userId == null) {
            throw new BusinessException("USER_ID_REQUIRED", "Authenticated user is required", HttpStatus.UNAUTHORIZED);
        }

        int currentPage = page == null ? DEFAULT_PAGE : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (currentPage < 1) {
            throw new BusinessException("INVALID_PAGE", "page must be greater than or equal to 1");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new BusinessException("INVALID_PAGE_SIZE", "size must be between 1 and 100");
        }
        if (startTime != null && endTime != null && startTime.after(endTime)) {
            throw new BusinessException("INVALID_TIME_RANGE", "startTime must be before endTime");
        }

        RequestLogQuery query = new RequestLogQuery();
        query.setUserId(userId);
        query.setModelId(modelId);
        query.setStatusCode(statusCode);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setLimit(pageSize);
        query.setOffset((currentPage - 1) * pageSize);

        long total = requestLogMapper.countRequestLogs(query);
        List<RequestLog> logs = requestLogMapper.searchRequestLogs(query);

        RequestLogPageResponse response = new RequestLogPageResponse();
        response.setItems(toResponses(logs));
        response.setPage(currentPage);
        response.setSize(pageSize);
        response.setTotal(total);
        response.setTotalPages((int) Math.ceil((double) total / pageSize));
        return response;
    }

    private List<RequestLogResponse> toResponses(List<RequestLog> logs) {
        List<RequestLogResponse> responses = new ArrayList<>();
        for (RequestLog log : logs) {
            RequestLogResponse response = new RequestLogResponse();
            response.setId(log.getId());
            response.setRequestId(log.getRequestId());
            response.setUserId(log.getUserId());
            response.setApiKeyId(log.getApiKeyId());
            response.setProviderId(log.getProviderId());
            response.setModelId(log.getModelId());
            response.setStatusCode(log.getStatusCode());
            response.setLatencyMs(log.getLatencyMs());
            response.setErrorCode(log.getErrorCode());
            response.setCreatedAt(log.getCreatedAt() == null ? null : log.getCreatedAt().toString());
            responses.add(response);
        }
        return responses;
    }
}
