package com.offertracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offertracker.entity.AiInterviewRuntimeSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiInterviewRuntimeSnapshotMapper extends BaseMapper<AiInterviewRuntimeSnapshot> {
    @Select("SELECT * FROM ai_interview_runtime_snapshots WHERE session_id = #{sessionId}")
    AiInterviewRuntimeSnapshot findBySessionId(Long sessionId);

    /** Increments the version in the database so concurrent writers cannot rewind it. */
    @Update("UPDATE ai_interview_runtime_snapshots SET version = version + 1, state_json = #{stateJson}, updated_at = #{updatedAt} WHERE session_id = #{sessionId}")
    int updateState(@Param("sessionId") Long sessionId, @Param("stateJson") String stateJson, @Param("updatedAt") LocalDateTime updatedAt);

    @Select("SELECT version FROM ai_interview_runtime_snapshots WHERE session_id = #{sessionId}")
    Long selectVersion(@Param("sessionId") Long sessionId);
}
