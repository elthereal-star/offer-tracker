package com.offertracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offertracker.entity.AiInterviewRuntimeSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiInterviewRuntimeSnapshotMapper extends BaseMapper<AiInterviewRuntimeSnapshot> {
    @Select("SELECT * FROM ai_interview_runtime_snapshots WHERE session_id = #{sessionId}")
    AiInterviewRuntimeSnapshot findBySessionId(Long sessionId);
}
