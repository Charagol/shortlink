package com.charagol.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.charagol.shortlink.admin.dao.entity.GroupDO;
import com.charagol.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.charagol.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.charagol.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;

import java.util.List;

/**
 * 短链接分组接口层
 */
public interface GroupService extends IService<GroupDO> {

    /**
     * 新增短链接分组
     * @param groupName 短链接分组名称
     */
    void saveGroup(String groupName);


    /**
     * 新增短链接分组
     * @param username 用户名
     * @param groupName 短链接分组名称
     */
    void saveGroup(String username, String groupName);


    /**
     * 查询短链接分组集合
     * @return 短链接分组集合
     */
    List<ShortLinkGroupRespDTO> listGroup();

    /**
     * 更新短链接分组信息
     * @param requestParam
     */
    void updateGroup(ShortLinkGroupUpdateReqDTO requestParam);


    /**
     * 删除短链接分组
     * @param gid 短链接分组ID
     */
    void deleteGroup(String gid);


    /**
     * 排序短链接分组
     * @param requestParam 排序请求参数
     */
    void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam);
}
