package com.offertracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offertracker.entity.AiInterviewRequestIdempotency;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiInterviewRequestIdempotencyMapper extends BaseMapper<AiInterviewRequestIdempotency> { }
