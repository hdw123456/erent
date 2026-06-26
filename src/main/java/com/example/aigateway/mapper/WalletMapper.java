package com.example.aigateway.mapper;

import com.example.aigateway.dto.LowBalanceUser;
import com.example.aigateway.entity.Wallet;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WalletMapper {
    void insertWallet(Wallet wallet);
    Wallet getWalletByUserId(@Param("userId") long userId);
    Wallet getWalletByUserIdForUpdate(@Param("userId") long userId);
    BigDecimal getBalanceByUserId(@Param("userId") long userId);
    List<LowBalanceUser> getLowBalanceUsersWithEnabledApiKeys(@Param("minBalance") BigDecimal minBalance);
    void updateBalance(@Param("userId") long userId, @Param("balance") BigDecimal balance);
}
