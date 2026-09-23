package com.hnieacm.discussion.feign.dto;

import lombok.Data;

/** Display fields from the user service.
 * @author HnieOJ contributors
 */
@Data
public class UserBasicDto {
    private String uid;
    private String username;
}
