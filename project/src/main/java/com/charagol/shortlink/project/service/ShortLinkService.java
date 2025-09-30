/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.charagol.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.charagol.shortlink.project.dao.entity.ShortLinkDO;
import com.charagol.shortlink.project.dto.req.ShortLinkBatchCreateReqDTO;
import com.charagol.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.charagol.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.charagol.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.charagol.shortlink.project.dto.resp.ShortLinkBatchCreateRespDTO;
import com.charagol.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.charagol.shortlink.project.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.charagol.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * 短链接接口层
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：link）获取项目资料
 */
public interface ShortLinkService extends IService<ShortLinkDO> {

    /**
     * 创建短链接
     *
     * @param requestParam 创建短链接请求参数
     * @return 短链接创建信息
     */
    ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam);


    /**
     * 批量创建短链接
     *
     * @param requestParam 批量创建短链接请求参数
     * @return 批量创建短链接返回参数
     */
    ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam);


    /**
     * 修改短链接
     * @param requestParam
     */
    void updateShortLink(ShortLinkUpdateReqDTO requestParam);


    /**
     * 分页查询短链接
     *
     * @param requestParam
     * @return
     */
    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam);

    /**
     * 根据gid查询短链接数量
     * @param requestParam
     * @return
     */
    List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> requestParam);


    /**
     * 短链接跳转
     * @param shortUri 短链接后缀
     * @param request HTTP请求
     * @param response HTTP响应
     */
    void restoreUrl(String shortUri, HttpServletRequest request, HttpServletResponse response);
}
