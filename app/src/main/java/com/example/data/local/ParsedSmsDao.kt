package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ParsedSmsDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(sms: ParsedSmsEntity)

  @Query("SELECT * FROM parsed_sms WHERE createdAt > :from ORDER BY createdAt DESC")
  fun getAfter(from: Long): Flow<List<ParsedSmsEntity>>

  @Query("SELECT * FROM parsed_sms WHERE color = 'YELLOW' AND isUserCorrected = 0")
  fun getPendingReview(): Flow<List<ParsedSmsEntity>>

  @Query("SELECT * FROM user_corrections WHERE senderNumber = :senderNumber")
  suspend fun getCorrectionsForSender(senderNumber: String): List<UserCorrectionEntity>

  @Query("SELECT * FROM user_corrections WHERE senderNumber = :senderNumber AND fieldName = :fieldName")
  suspend fun getCorrectionsForSenderAndField(senderNumber: String, fieldName: String): List<UserCorrectionEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCorrection(correction: UserCorrectionEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMerchantRule(rule: MerchantRuleEntity)

  @Query("SELECT * FROM merchant_rules WHERE merchantPattern = :pattern LIMIT 1")
  suspend fun getMerchantRule(pattern: String): MerchantRuleEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSenderOverride(override: SenderOverrideEntity)

  @Query("SELECT * FROM sender_overrides WHERE senderPattern = :sender LIMIT 1")
  suspend fun getSenderOverride(sender: String): SenderOverrideEntity?

  @Query("SELECT * FROM sender_overrides")
  suspend fun getAllSenderOverrides(): List<SenderOverrideEntity>
}
