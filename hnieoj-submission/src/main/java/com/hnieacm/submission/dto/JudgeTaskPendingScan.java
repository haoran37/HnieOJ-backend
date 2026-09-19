package com.hnieacm.submission.dto;

import java.util.List;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/18
 * @Description: 有界可推进的 PEL 扫描结果：本批次清理条目、下一批游标与是否已到 PEL 末尾
 */
public record JudgeTaskPendingScan(List<JudgeTaskStreamEntry> entries, String nextCursor, boolean exhausted) {
}
