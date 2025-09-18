package com.charagol.shortlink.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import com.charagol.shortlink.admin.common.convention.exception.ServiceException;
import com.charagol.shortlink.admin.common.convention.result.Result;
import com.charagol.shortlink.admin.common.convention.result.Results;
import com.charagol.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.charagol.shortlink.admin.dto.req.UserLoginReqDTO;
import com.charagol.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.charagol.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.charagol.shortlink.admin.dto.resp.UserActualRespDTO;
import com.charagol.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.charagol.shortlink.admin.dto.resp.UserRespDTO;
import com.charagol.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shortlink/v1")
public class UserController {

    private final UserService userService;

    /**
     * 获取用户信息（加密版）
     * @param username
     * @return
     */
    @GetMapping("/user/{username}")
    public Result<UserRespDTO> getUserByUsername(@PathVariable("username") String username){

        UserRespDTO result = userService.getUserByUsername(username);
        if (result == null) {
            throw new ServiceException(UserErrorCodeEnum.USER_NULL);
        }else{
            return Results.success(result);         // 调用results带返回数据的成功响应的有参构造，将result封装进data
        }
    }

    /**
     *  获取用户信息（明文版）
     * @param username
     * @return
     */
    @GetMapping("/actual/user/{username}")
    public Result<UserActualRespDTO> getActualUserByUsername(@PathVariable("username") String username){
            return Results.success(BeanUtil.toBean(userService.getUserByUsername(username),UserActualRespDTO.class));
            // tobean(源对象,目标类型)。对象转换：将源对象(source)转换为目标类型(targetClass)的实例
            // 自动匹配源对象和目标对象的属性名进行值拷贝，并返回目标对象。
            // 因此：
            // 等价于return Results.success(BeanUtil.toBean(UserRespDTO,UserActualRespDTO));
            // 等价于return Results.success(UserActualRespDTO);
            // 等价于return UserActualRespDTO;
    }


    /**
     * 判断用户名是否存在
     * @param username
     * @return
     */
    @GetMapping("/has-username")
    public Result<Boolean> hasUsername(@RequestParam("username") String username){
        Boolean result = userService.hasUsername(username);
        log.info("hasUsername: {}, result: {}", username, result);
        return Results.success(userService.hasUsername(username));
    }


    /**
     * 用户注册
     *
     * @param requestParam 用户注册请求参数
     * @return 成功响应
     */
    @PostMapping ("/user")
    public Result<Void> register(@RequestBody UserRegisterReqDTO requestParam){
        userService.register(requestParam);
        return Results.success();
    }


    /**
     * 用户更新
     * @param requestParam
     * @return
     */
    @PutMapping("/user")
    public Result<Void> update(@RequestBody UserUpdateReqDTO requestParam){
        userService.update(requestParam);
        return Results.success();
    }

    /**
     * 用户登录
     * @param requestParam
     * @return
     */
    @PostMapping("/user/login")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO requestParam){
        UserLoginRespDTO result = userService.login(requestParam);
        return Results.success(result);
    }


    /**
     *  用户登录校验
     * @param username
     * @param token
     * @return
     */
    @GetMapping("/user/check-login")
    public Result<Boolean> checkLogin(@RequestParam("username") String username,@RequestParam("token") String token){
        return Results.success(userService.checkLogin(username,token));
    }


    /**
     *  用户登出
     * @param username
     * @param token
     * @return
     */
    @DeleteMapping("/user/logout")
    public Result<Void> logout(@RequestParam("username") String username, @RequestParam("token") String token){
        userService.logout(username,token);
        return Results.success();
    }

}
