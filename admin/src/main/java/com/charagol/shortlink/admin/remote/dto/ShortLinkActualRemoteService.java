package com.charagol.shortlink.admin.remote.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charagol.shortlink.admin.common.convention.result.Result;
import com.charagol.shortlink.admin.dto.req.RecycleBinRecoverReqDTO;
import com.charagol.shortlink.admin.dto.req.RecycleBinRemoveReqDTO;
import com.charagol.shortlink.admin.dto.req.RecycleBinSaveReqDTO;
import com.charagol.shortlink.admin.remote.dto.req.*;
import com.charagol.shortlink.admin.remote.dto.resp.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient("short-link-project")
public interface ShortLinkActualRemoteService {
    /**
     * 创建短链接
     *
     * @param requestParam 创建短链接请求参数
     * @return 短链接创建响应
     */
    @PostMapping("/api/short-link/v1/create")
    Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam);


    /**
     * 批量创建短链接
     *
     * @param requestParam 批量创建短链接请求参数
     * @return 短链接批量创建响应
     */
    @PostMapping("/api/short-link/v1/create/batch")
    Result<ShortLinkBatchCreateRespDTO> batchCreateShortLink(@RequestBody ShortLinkBatchCreateReqDTO requestParam);

    /**
     * 修改短链接
     *
     * @param requestParam 短链接修改请求参数
     */
    @PostMapping("/api/short-link/v1/update")
    void updateShortLink(@RequestBody ShortLinkUpdateReqDTO requestParam);

    /**
     * 分页查询短链接
     *
     * @param gid      分组标识
     * @param orderTag 排序类型
     * @param current  当前页
     * @param size     当前数据多少
     * @return 短链接分页查询响应
     * <p>
     * Note
     *  1. 为什么方法参数要加@RequestParam
     *  2. 为什么不能用IPage，而是得用Page类来接收？
     */
    @GetMapping("/api/short-link/v1/page")
    Result<Page<ShortLinkPageRespDTO>> pageShortLink(
            @RequestParam("gid") String gid,
            @RequestParam("orderTag") String orderTag,
            @RequestParam("current") Long current,
            @RequestParam("size") Long size
    );

    /**
     * 查询分组短链接总量
     *
     * @param requestParam 分组查询短链接总量请求参数
     * @return 分组短链接总量查询响应
     */
    @GetMapping("/api/short-link/v1/count")
    Result<List<ShortLinkGroupCountQueryRespDTO>> listGroupShortLinkCount(@RequestParam("requestParam") List<String> requestParam);


    /**
     * 根据 URL 获取标题
     *
     * @param url 目标网站地址
     * @return 网站标题
     */
    @GetMapping("/api/short-link/v1/title")
    Result<String> getTitleByUrl(@RequestParam("url") String url);


    /**
     * 保存回收站
     *
     * @param requestParam 回收站保存请求参数
     */
    @PostMapping("/api/short-link/v1/recycle-bin/save")
    void saveRecycleBin(@RequestBody RecycleBinSaveReqDTO requestParam);

    /**
     * 从回收站恢复短链接
     *
     * @param requestParam 短链接恢复请求参数
     */
    @PostMapping("/api/short-link/v1/recycle-bin/recover")
    void recoverRecycleBin(@RequestBody RecycleBinRecoverReqDTO requestParam);

    /**
     * 从回收站移除短链接
     *
     * @param requestParam 短链接移除请求参数
     */
    @PostMapping("/api/short-link/v1/recycle-bin/remove")
    void removeRecycleBin(@RequestBody RecycleBinRemoveReqDTO requestParam);

    /**
     * 分页查询回收站
     *
     * @param gidList 分组标识集合
     * @param current 当前页
     * @param size    当前数据多少
     * @return 回收站分页查询响应
     */
    @GetMapping("/api/short-link/v1/recycle-bin/page")
    Result<Page<ShortLinkPageRespDTO>> pageRecycleBinShortLink(@RequestParam("gidList") List<String> gidList,
                                                               @RequestParam("current") Long current,
                                                               @RequestParam("size") Long size);


    /**
     * 访问单个短链接指定时间内监控数据
     *
     * @param fullShortUrl 完整短链接
     * @param gid          分组标识
     * @param startDate    开始日期
     * @param endDate      结束日期
     * @return 短链接监控信息
     */
    @GetMapping("/api/short-link/v1/stats")
    Result<ShortLinkStatsRespDTO> oneShortLinkStats(
            @RequestParam("fullShortUrl") String fullShortUrl,
            @RequestParam("gid") String gid,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );

    /**
     * 访问单个短链接指定时间内监控访问记录数据
     *
     * @return 短链接监控访问记录信息
     */
    @GetMapping("/api/short-link/v1/stats/access-record")
    Result<Page<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(
            @RequestParam("fullShortUrl") String fullShortUrl,
            @RequestParam("gid") String gid,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );

    /**
     * NOTE
     *  1. 远程调用里的GET请求如果有对象作为参数，可以不需要拆开
     *  2. Spring Cloud OpenFeign 提供了等效的@SpringQueryMap注解，用于将 POJO 或 Map ，映射为 GET 方法的参数。
     */

    /**
     * 访问分组短链接指定时间内监控数据
     * @param requestParam 访分组问短链接监控请求参数
     * @return 分组短链接监控信息
     */
    @GetMapping("/api/short-link/v1/stats/group")
    Result<ShortLinkStatsRespDTO> groupShortLinkStats(
            @SpringQueryMap ShortLinkGroupStatsReqDTO requestParam
    );

    /**
     * 访问分组短链接指定时间内监控访问记录数据
     * @param requestParam 访问分组短链接监控访问记录请求参数
     * @return 分组短链接监控访问记录信息
     */
    @GetMapping("/api/short-link/v1/stats/access-record/group")
    Result<Page<ShortLinkStatsAccessRecordRespDTO>> shortLinkGroupStatsAccessRecord(
            @SpringQueryMap ShortLinkGroupStatsAccessRecordReqDTO requestParam
    );
}
