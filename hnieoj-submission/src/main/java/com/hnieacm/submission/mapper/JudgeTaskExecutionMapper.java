package com.hnieacm.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hnieacm.submission.entity.JudgeTaskExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 判题任务执行租约 mapper
 */
@Mapper
public interface JudgeTaskExecutionMapper extends BaseMapper<JudgeTaskExecution> {

    /**
     * 以提交展示 ID 加行锁读取执行租约，保证所有权与额度转移串行化。
     *
     * @param submissionId 提交展示 ID
     * @return 执行租约；不存在时返回 null
     */
    @Select("SELECT * FROM judge_task_execution WHERE submission_id = #{submissionId} LIMIT 1 FOR UPDATE")
    JudgeTaskExecution selectBySubmissionIdForUpdate(@Param("submissionId") String submissionId);
}
