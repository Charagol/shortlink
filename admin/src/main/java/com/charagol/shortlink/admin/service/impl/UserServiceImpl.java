package com.charagol.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charagol.shortlink.admin.common.convention.exception.ClientException;
import com.charagol.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.charagol.shortlink.admin.dao.entity.UserDO;
import com.charagol.shortlink.admin.dao.mapper.UserMapper;
import com.charagol.shortlink.admin.dto.req.UserLoginReqDTO;
import com.charagol.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.charagol.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.charagol.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.charagol.shortlink.admin.dto.resp.UserRespDTO;
import com.charagol.shortlink.admin.service.GroupService;
import com.charagol.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.charagol.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static com.charagol.shortlink.admin.common.enums.UserErrorCodeEnum.USER_EXIST;
import static com.charagol.shortlink.admin.common.enums.UserErrorCodeEnum.USER_NAME_EXIST;

/**
 * 用户接口实现层
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final GroupService groupService;

    // 继承自extends ServiceImpl<UserMapper, UserDO>，所以可以直接调用baseMapper中的方法
    @Override
    public UserRespDTO getUserByUsername(String username) {
        // 1. 构建查询条件
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getUsername, username);
        // 2. 查询用户信息
        UserDO userDO = baseMapper.selectOne(queryWrapper);

        // 3. 报错判断返回异常
        if (userDO == null) {
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
        }

        // 4. 构建返回结果对象
        UserRespDTO result = new UserRespDTO();

        // 5. 将UserDO对象中的属性复制到UserRespDTO对象中
        BeanUtils.copyProperties(userDO, result);
            // BeanUtils是一个静态工具类，类中的静态方法可以直接通过类名来调用，不需要实例化对象。
            // 使用：类名.方法名()，比如Math.random()

        return result;
    }

    @Override
    public Boolean hasUsername(String username) {
        /*
        // 1. 构建查询条件
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                        .eq(UserDO::getUsername, username);
        // 2. 查询用户信息
        UserDO userDO = baseMapper.selectOne(queryWrapper);

        // 3. 返回判断结果：
            // 如果用户信息存在，返回true；如果用户信息不存在，返回false
        return userDO != null;
        */

        // 直接查询布隆过滤器
        // 只有当 username 已经被 add 到布隆过滤器中时，这里才会返回 true
        return userRegisterCachePenetrationBloomFilter.contains(username);
    }

    @Override
    public void register(UserRegisterReqDTO requestParam) {
        //1. 判断用户名是否存在
        if (hasUsername(requestParam.getUsername())) {
            throw new ClientException(USER_NAME_EXIST);
        }
        //2. 拿锁，防止并发注册
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());
        try {
            // 3. 尝试加锁，如果加锁成功则返回 true，从而执行。
            if (lock.tryLock()) {
                try {
                    //3. 插入数据库并判断插入结果
                    int inserted = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
                    if (inserted < 1) {
                        throw new ClientException(UserErrorCodeEnum.USER_SAVE_ERROR);
                    }

                } catch (DuplicateKeyException e) {
                    throw new ClientException(USER_EXIST);
                }
                //4. 成功注册后，将用户名添加到布隆过滤器中
                userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
                // 5, 调用groupService.saveGroup方法，创建默认分组
                // NEW! 2025-09-13 21:40
                groupService.saveGroup(requestParam.getUsername(),"默认分组");
                return;
            }
            throw new ClientException(USER_NAME_EXIST);
        // TODO 之前的逻辑有误：
            // 1. 尝试加锁，成功就return，失败则隐式掉落异常
            // 2. try中的代码无论怎么样一定会执行finally
            // 3. 即使没有加锁，也会执行finally中的解锁，导致报错
        }finally {
            lock.unlock();
        }
    }

    @Override
    // TODO 验证当前用户名是否为登录用户
    public void update(UserUpdateReqDTO requestParam) {
        LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername());
        baseMapper.update(BeanUtil.toBean(requestParam, UserDO.class), updateWrapper);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {

        // 1， 执行登录逻辑：如果没报错就是登录成功，继续执行下面的返回UUID的逻辑
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getPassword, requestParam.getPassword())
                .eq(UserDO::getDelFlag, 0);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        // 2. 追加判断是否登录成功
        if (userDO == null) {
            throw new ClientException(UserErrorCodeEnum.USER_NULL);
            }


        // 3. 生成token逻辑：
        // 3.1 生成UUID作为token
        // 3.2 将用户信息与UUID绑定，存入redis中，设置过期时间
        // 3.3 返回UserLoginRespDTO对象，包含token
        /**
         * Hash
         * Key: login_用户名
         * Value：
         *  Key: token标识
         *  Value：JSON字符串（用户信息）
         */
        String uuid = UUID.randomUUID().toString();
        // TODO 设置过期时间，默认30L,TimeUnit.MINUTES
        // 需要 import java.util.concurrent.TimeUnit;  JDK21不支持

//        stringRedisTemplate.opsForValue().set(uuid, JSON.toJSONString(userDO));
        stringRedisTemplate.opsForHash().put("login_" + requestParam.getUsername(), uuid, JSON.toJSONString(userDO));
        stringRedisTemplate.expire("login_" + requestParam.getUsername(), 30, TimeUnit.MINUTES);

//        Map<String, String> userInfoMap = new HashMap<>();
//        userInfoMap.put("token", JSON.toJSONString(userDO));

        return new UserLoginRespDTO(uuid);
    }

    @Override
    public Boolean checkLogin(String username,String token) {
        return stringRedisTemplate.opsForHash().get("login_" + username,token) != null;
    }

    @Override
    public void logout(String username, String token) {
        if (checkLogin(username, token)) {
            stringRedisTemplate.opsForHash().delete("login_" + username, token);
            return;
        }
        throw new ClientException("用户token不存在或用户未登录");

    }
}
