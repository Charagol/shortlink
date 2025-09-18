package com.charagol.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.charagol.shortlink.admin.dao.entity.UserDO;
import com.charagol.shortlink.admin.dto.req.UserLoginReqDTO;
import com.charagol.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.charagol.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.charagol.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.charagol.shortlink.admin.dto.resp.UserRespDTO;

public interface UserService extends IService<UserDO> {
    /**
     * 根据用户名查询用户信息
     *
     * @param username 用户名
     * @return 用户返回实体
     */
    UserRespDTO getUserByUsername(String username);

    /**
     * 根据用户名判断是否存在
     * @param username 用户名
     * @return true：存在； false：不存在
     */
    Boolean hasUsername(String username);


    /**
     * 注册用户
     * @param requestParam 注册请求参数
     */
    void register(UserRegisterReqDTO requestParam);

    /**
     * 更新用户信息
     * @param requestParam
     */
    void update(UserUpdateReqDTO requestParam);

    /**
     * 用户登录
     *
     * @param requestParam 登录请求参数
     * @return 登录返回实体
     */
    UserLoginRespDTO login(UserLoginReqDTO requestParam);

    /**
     * 检查登录状态
     * @param username 用户名
     * @param token 用户标识
     */
    Boolean checkLogin(String username,String token);

    /**
     * 退出登录
     * @param username
     * @param username 用户名
     * @param token 用户标识
     */
    void logout(String username, String token);
}
