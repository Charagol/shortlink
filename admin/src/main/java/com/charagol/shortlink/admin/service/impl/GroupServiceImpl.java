package com.charagol.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charagol.shortlink.admin.common.biz.user.UserContext;
import com.charagol.shortlink.admin.common.convention.result.Result;
import com.charagol.shortlink.admin.dao.entity.GroupDO;
import com.charagol.shortlink.admin.dao.mapper.GroupMapper;
import com.charagol.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.charagol.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.charagol.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.charagol.shortlink.admin.remote.dto.ShortLinkRemoteService;
import com.charagol.shortlink.admin.remote.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.charagol.shortlink.admin.service.GroupService;
import com.charagol.shortlink.admin.toolkit.RandomGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 短链接分组 ServiceImpl
 */
@Service
@Slf4j
public class GroupServiceImpl extends ServiceImpl<GroupMapper,GroupDO> implements GroupService {

    // NEW! 2025-09-13 21:24
    ShortLinkRemoteService shortLinkRemoteService = new ShortLinkRemoteService() {
    };


    /**
     * 新增短链接分组
     * @param groupName 短链接分组名称
     */
    // NEW! 2025-09-13 21:50
    @Override
    public void saveGroup(String groupName) {
        saveGroup(UserContext.getUsername(), groupName);
    }

    // NEW! 2025-09-13 21:50  新增方法，将原有的 saveGroup 方法修改为 saveGroup(String username, String groupName)
    @Override
    public void saveGroup(String username, String groupName) {
        String uniqueGid;
        // 1. 生成 gid
        do{
            uniqueGid = RandomGenerator.generateRandom();
            // 2. 判断 gid 是否存在,hasGid(gid)如果存在返回 true，则重新生成 gid
            // 3. 直到不存在返回 false，跳出循环
        }while (hasGid(username,uniqueGid));

        GroupDO groupDO = GroupDO.builder()
                .name(groupName)
                .gid(uniqueGid)
                .username(username)   // TODO 设置用户名
                .sortOrder(0)
                .build();
        baseMapper.insert(groupDO);
    }


    /**
     * 获取短链接分组列表
     * @return
     */
/*    初始版本代码
    @Override
    public List<ShortLinkGroupRespDTO> listGroup() {
        // TODO 获取用户名
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getDelFlag, 0)
                .orderByDesc(GroupDO::getSortOrder,GroupDO::getUpdateTime);
        List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);
        return BeanUtil.copyToList(groupDOList, ShortLinkGroupRespDTO.class);
    }
    */

    /**
     * 获取短链接分组列表
     *
     * @return 返回包含短链接数量的短链接分组列表
     */
    // NEW! 2025-09-13 21:32 将上面注释的代码修改为以下代码
    @Override
    public List<ShortLinkGroupRespDTO> listGroup() {
        // 1. 查询出当前登录用户 所有的分组 的完全信息，封装在 GroupDO 的列表中 ——> groupDOList
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getDelFlag, 0)
                .orderByDesc(GroupDO::getSortOrder,GroupDO::getUpdateTime);
        List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);

        // 2. 远程调用：获取各分组的链接数
        // listGroupShortLinkCount需要传入一个 gid 列表：List<String>
        // groupDOList.stream().map(GroupDO::getGid).toList() 得到 ——> [ "gid_1", "gid_2" ]
        List<String> gidList = groupDOList.stream().map(GroupDO::getGid).toList();

        Result<List<ShortLinkGroupCountQueryRespDTO>> listResult = shortLinkRemoteService
                .listGroupShortLinkCount(gidList);

        // 3. 合并结果
        // 3.1 先把基础分组信息转换成最终需要的格式：shortLinkGroupRespDTOList
        //      copyToList ——> 提取了关键信息，但缺少 shortLinkCount
        List<ShortLinkGroupRespDTO> shortLinkGroupRespDTOList =
                BeanUtil.copyToList(groupDOList, ShortLinkGroupRespDTO.class);

        // 3.2 遍历 shortLinkGroupRespDTOList，根据 gid 找到对应的 ShortLinkGroupCountQueryRespDTO，并设置 shortLinkCount
        shortLinkGroupRespDTOList.forEach(each -> {
            Optional<ShortLinkGroupCountQueryRespDTO> first = listResult.getData().stream()
                    .filter(item -> Objects.equals(item.getGid(), each.getGid()))
                    .findFirst();
            first.ifPresent(item -> each.setShortLinkCount(item.getShortLinkCount()));
        });
        return shortLinkGroupRespDTOList;
    }



    /**
     * 更新短链接分组
     * @param requestParam
     */
    @Override
    public void updateGroup(ShortLinkGroupUpdateReqDTO requestParam) {
        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getGid, requestParam.getGid())
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getDelFlag, 0);
        GroupDO groupDO = new GroupDO();
        groupDO.setName(requestParam.getName());
        baseMapper.update(groupDO, updateWrapper);
    }


    /**
     * 删除短链接分组
     * @param gid
     */
    @Override
    public void deleteGroup(String gid) {
        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getDelFlag, 0);
        GroupDO groupDO = new GroupDO();
        groupDO.setDelFlag(1);                         // 标记删除
        baseMapper.update(groupDO, updateWrapper);
    }


    /**
     * 排序短链接分组
     * @param requestParam
     */
    @Override
    public void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam) {
        requestParam.forEach(each -> {
            GroupDO groupDO = GroupDO.builder()
                    .sortOrder(each.getSortOrder())
                    .build();
            LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                    .eq(GroupDO::getUsername, UserContext.getUsername())
                    .eq(GroupDO::getGid, each.getGid())
                    .eq(GroupDO::getDelFlag, 0);
            baseMapper.update(groupDO, updateWrapper);
        });
    }

    // NEW! 2025-09-13 21:32  将原有的 hasGid 方法修改为 hasGid(String username, String gid)
    // NEW! Wrappers条件改为eq(GroupDO::getUsername,Optional.ofNullable(username).orElse(UserContext.getUsername()));
    private boolean hasGid(String username, String gid) {
            // 1. 构建查询条件
            LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                    .eq(GroupDO::getGid, gid)
                    // TODO 设置用户名
                    .eq(GroupDO::getUsername,Optional.ofNullable(username).orElse(UserContext.getUsername()));
            GroupDO hasGroupFlag = baseMapper.selectOne(queryWrapper);
            // 2. 判断是否存在，如果存在，返回 true，否则返回 false
            return hasGroupFlag != null;
    }
}
