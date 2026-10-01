package com.offertracker.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offertracker.entity.AiInterviewSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper public interface AiInterviewSessionMapper extends BaseMapper<AiInterviewSession>{
 @Select("SELECT id FROM ai_interview_sessions WHERE id = #{id} AND (#{ownerId} IS NULL OR owner_id = #{ownerId}) FOR UPDATE")
 Long selectIdForUpdate(@Param("id") Long id, @Param("ownerId") Long ownerId);
}
