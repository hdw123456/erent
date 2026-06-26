package com.example.aigateway.mapper;

import com.example.aigateway.entity.UsageRecord;
import com.example.aigateway.dto.UserDailyUsageSummary;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UsageRecordMapper {
    void insertUsageRecord(UsageRecord usageRecord);

    List<UserDailyUsageSummary> getUserDailyUsage(
            @Param("userId") Long userId,
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime
    );
}
