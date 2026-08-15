package com.offertracker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.offertracker.entity.JobApplication;
import com.offertracker.enums.ApplicationStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface JobApplicationMapper extends BaseMapper<JobApplication> {

    @Select("SELECT id FROM job_applications WHERE id = #{id} FOR UPDATE")
    Long selectIdForUpdate(@Param("id") Long id);

    @Select("""
            <script>
            SELECT a.*
            FROM job_applications a
            JOIN companies c ON c.id = a.company_id
            WHERE 1 = 1
            <if test="status != null">AND a.status = #{status}</if>
            <if test="companyId != null">AND a.company_id = #{companyId}</if>
            <if test="keyword != null and keyword != ''">
              AND (LOWER(c.name) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                OR LOWER(a.position) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                OR LOWER(a.city) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                OR LOWER(a.source) LIKE CONCAT('%', LOWER(#{keyword}), '%'))
            </if>
            <if test="city != null and city != ''">AND LOWER(a.city) = LOWER(#{city})</if>
            <if test="source != null and source != ''">AND LOWER(a.source) = LOWER(#{source})</if>
            <if test="appliedFrom != null">AND a.applied_at &gt;= #{appliedFrom}</if>
            <if test="appliedTo != null">AND a.applied_at &lt;= #{appliedTo}</if>
            ORDER BY a.updated_at DESC
            </script>
            """)
    Page<JobApplication> selectFilteredPage(
            Page<JobApplication> page,
            @Param("status") ApplicationStatus status,
            @Param("companyId") Long companyId,
            @Param("keyword") String keyword,
            @Param("city") String city,
            @Param("source") String source,
            @Param("appliedFrom") LocalDate appliedFrom,
            @Param("appliedTo") LocalDate appliedTo);
}
