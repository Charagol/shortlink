package com.charagol.shortlink.admin.dto.resp;


import com.charagol.shortlink.admin.common.serialize.PhoneDesensitizationSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * DTO：Data Transfer Object。根据前端需要，用于在不同层之间传递数据。
 * 类名以 DTO 结尾，如 UserRespDTO，通常细分为 ReqDTO（请求）和 RespDTO（响应）。
 *
 * req: 封装需要请求前端的数据
 * resp: 封装需要响应给前端的数据
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRespDTO {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 手机号
     */
    @JsonSerialize(using = PhoneDesensitizationSerializer.class)
    private String phone;

    /**
     * 邮箱
     */
    private String mail;
}
