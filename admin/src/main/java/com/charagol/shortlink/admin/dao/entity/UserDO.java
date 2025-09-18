package com.charagol.shortlink.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.charagol.shortlink.admin.common.database.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DO:Data Object 实体类，等价于pojo entity类
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_user")
public class UserDO extends BaseDO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;
    /**
     *
     * 真实姓名
     */
    private String realName;
    /**
     * 手机号
     */
    private String phone;
    /**
     * 邮箱
     */
    private String mail;
    /**
     * 注销时间戳
     */
    private Long deletionTime;

}