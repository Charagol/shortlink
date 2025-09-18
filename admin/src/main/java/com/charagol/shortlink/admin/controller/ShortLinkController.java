package com.charagol.shortlink.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.charagol.shortlink.admin.common.convention.result.Result;
import com.charagol.shortlink.admin.remote.dto.ShortLinkRemoteService;
import com.charagol.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.charagol.shortlink.admin.remote.dto.req.ShortLinkPageReqDTO;
import com.charagol.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import com.charagol.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ShortLinkController {

    /**
     * TODO 后续重构为Spring Cloud Feign调用
     */
    ShortLinkRemoteService shortLinkRemoteService = new ShortLinkRemoteService() {
    };

    /**
     * 创建短链
     * @param requestParam 短链接创建参数
     * @return Result
     */
    @PostMapping("/api/short-link/admin/v1/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return shortLinkRemoteService.createShortLink(requestParam);
    }


    /**
     * 分页查询短链
     * @param requestParam 分页查询参数
     * @return Result
     */
    @GetMapping("/api/short-link/admin/v1/page")
    public Result<IPage<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO requestParam) {
        return shortLinkRemoteService.pageShortLink(requestParam);
    }
}
