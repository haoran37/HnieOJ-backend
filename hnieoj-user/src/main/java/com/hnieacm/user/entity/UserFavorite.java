package com.hnieacm.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author HnieOJ contributors
 */
@Data
@TableName("user_favorite")
public class UserFavorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uid;
    private String targetType;
    private String targetId;
    private LocalDateTime gmtCreate;
}
