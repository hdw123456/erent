package com.example.aigateway.mapper;

import com.example.aigateway.dto.ModelUsageStats;
import com.example.aigateway.dto.RequestLogQuery;
import com.example.aigateway.entity.RequestLog;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** MyBatis persistence operations for request log data. */
public interface RequestLogMapper {
    void insertRequestLog(RequestLog requestLog);
    RequestLog getRequestLogByRequestId(@Param("requestId") String requestId);
    List<RequestLog> getRequestLogsByUserId(
            @Param("userId") long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );
    List<RequestLog> searchRequestLogs(RequestLogQuery query);
    long countRequestLogs(RequestLogQuery query);
    List<ModelUsageStats> getModelUsageStats(
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime
    );
}
