package com.charagol.shortlink.project.service;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.charagol.shortlink.project.dto.req.ShortLinkStatsAccessRecordReqDTO;
import com.charagol.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import com.charagol.shortlink.project.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import com.charagol.shortlink.project.dto.resp.ShortLinkStatsRespDTO;
import org.springframework.stereotype.Service;

/**
 * 短链接监控接口层
 */
@Service
public interface ShortLinkStatsService {

    /**
     * 获取单个短链接监控数据
     *
     * @param requestParam 获取短链接监控数据入参
     * @return 短链接监控数据
     */
    ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam);

    /**
     * 获取单个短链接指定时间内访问记录监控数据
     *
     * @param requestParam 获取短链接指定时间内访问记录监控数据入参
     * @return 短链接指定时间内访问记录监控数据
     */
    IPage<ShortLinkStatsAccessRecordRespDTO> shortLinkStatsAccessRecord(ShortLinkStatsAccessRecordReqDTO requestParam);
}