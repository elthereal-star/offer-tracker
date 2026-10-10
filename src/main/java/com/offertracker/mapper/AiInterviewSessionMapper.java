package com.offertracker.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offertracker.entity.AiInterviewSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
@Mapper public interface AiInterviewSessionMapper extends BaseMapper<AiInterviewSession>{
 @Select("SELECT id FROM ai_interview_sessions WHERE id = #{id} AND (#{ownerId} IS NULL OR owner_id = #{ownerId}) FOR UPDATE")
 Long selectIdForUpdate(@Param("id") Long id, @Param("ownerId") Long ownerId);

 /**
  * 带 fencing 校验的终态写入：只有令牌严格大于库中已记录值时才生效。
  *
  * <p>返回 0 表示本次写入来自已被取代的持有者（锁租约过期后仍在执行），调用方必须丢弃该结果。</p>
  */
 @Update("UPDATE ai_interview_sessions SET status = #{status}, average_score = #{averageScore}, report = #{report}, fence_token = #{token}, updated_at = #{updatedAt} WHERE id = #{id} AND fence_token < #{token}")
 int completeWithFence(@Param("id") Long id, @Param("status") String status, @Param("averageScore") Integer averageScore,
                       @Param("report") String report, @Param("updatedAt") LocalDateTime updatedAt, @Param("token") long token);

 @Select("SELECT fence_token FROM ai_interview_sessions WHERE id = #{id}")
 Long selectFenceToken(@Param("id") Long id);
}
