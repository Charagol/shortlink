package com.charagol.shortlink.admin.controller;

import com.charagol.shortlink.admin.common.convention.result.Result;
import com.charagol.shortlink.admin.common.convention.result.Results;
import com.charagol.shortlink.admin.dto.req.ShortLinkGroupSaveReqDTO;
import com.charagol.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.charagol.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.charagol.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.charagol.shortlink.admin.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * 短链接分组管理
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shortlink/admin/v1")
public class GroupController {

    private final GroupService groupService;


    /**
     * 新增短链接分组
     * @param requestParam
     * @return
     */
    @PostMapping("/group")
    public Result<Void> saveGroup(@RequestBody ShortLinkGroupSaveReqDTO requestParam) {
        groupService.saveGroup(requestParam.getName());
        return Results.success();
    }

    /**
     * 查询短链接分组列表
     * @return
     */
    @GetMapping("/group")
    public Result<List<ShortLinkGroupRespDTO>> listGroup() {
        return Results.success(groupService.listGroup());
    }


    /**
     * 修改短链接分组
     * @return
     */
    @PutMapping("/group")
    public Result<Void> updateGroup(@RequestBody ShortLinkGroupUpdateReqDTO requestParam) {
        groupService.updateGroup(requestParam);
        return Results.success();
    }


    /**
     * 删除短链接分组
     */
    @DeleteMapping("/group")
    public Result<Void> deleteGroup(@RequestParam String gid) {
        groupService.deleteGroup(gid);
        return Results.success();
    }


    /**
     * 排序短链接分组
     */
    @PostMapping("/group/sort")
    public Result<Void> sortGroup(@RequestBody List<ShortLinkGroupSortReqDTO> requestParam) {
        // 传入参数：所有人的GroupID和排序值的List
        groupService.sortGroup(requestParam);
        return Results.success();
    }
}
