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

package com.charagol.shortlink.project.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.charagol.shortlink.project.dao.entity.LinkAccessLogsDO;
import com.charagol.shortlink.project.dto.req.ShortLinkGroupStatsReqDTO;
import com.charagol.shortlink.project.dto.req.ShortLinkStatsReqDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 访问日志监控持久层
 */
@Mapper
public interface LinkAccessLogsMapper extends BaseMapper<LinkAccessLogsDO> {

    /**
     * 根据短链接获取指定日期内高频访问IP数据
     */
    @Select("SELECT " +
            "    ip, " +
            "    COUNT(ip) AS count " +
            "FROM " +
            "    t_link_access_logs " +
            "WHERE " +
            "    full_short_url = #{param.fullShortUrl} " +
            "    AND gid = #{param.gid} " +
            "    AND create_time BETWEEN STR_TO_DATE(CONCAT(#{param.startDate}, ' 00:00:00'), '%Y-%m-%d %H:%i:%s') " +
            "                        AND STR_TO_DATE(CONCAT(#{param.endDate}, ' 23:59:59'), '%Y-%m-%d %H:%i:%s') " +
            "GROUP BY " +
            "    full_short_url, gid, ip " +
            "ORDER BY " +
            "    count DESC " +
            "LIMIT 5;")
    List<HashMap<String, Object>> listTopIpByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 根据分组获取指定日期内高频访问IP数据
     */
    @Select("SELECT " +
            "    ip, " +
            "    COUNT(ip) AS count " +
            "FROM " +
            "    t_link_access_logs " +
            "WHERE " +
            "    gid = #{param.gid} " +
            "    AND create_time BETWEEN #{param.startDate} and #{param.endDate} " +
            "GROUP BY " +
            "    gid, ip " +
            "ORDER BY " +
            "    count DESC " +
            "LIMIT 5;")
    List<HashMap<String, Object>> listTopIpByGroup(@Param("param") ShortLinkGroupStatsReqDTO requestParam);


    /**
     * 根据短链接获取指定日期内新旧访客数据
     */
    @Select("SELECT " +
            "    SUM(old_user) AS oldUserCnt, " +
            "    SUM(new_user) AS newUserCnt " +
            "FROM ( " +
            "    SELECT " +
            "        CASE WHEN COUNT(DISTINCT DATE(create_time)) > 1 THEN 1 ELSE 0 END AS old_user, " +
            "        CASE WHEN COUNT(DISTINCT DATE(create_time)) = 1 AND MAX(create_time) >= STR_TO_DATE(CONCAT(#{param.startDate}, ' 00:00:00'), '%Y-%m-%d %H:%i:%s') " +
            "                  AND MAX(create_time) <= STR_TO_DATE(CONCAT(#{param.endDate}, ' 23:59:59'), '%Y-%m-%d %H:%i:%s') THEN 1 ELSE 0 END AS new_user " +
            "    FROM " +
            "        t_link_access_logs " +
            "    WHERE " +
            "        full_short_url = #{param.fullShortUrl} " +
            "        AND gid = #{param.gid} " +
            "    GROUP BY " +
            "        user " +
            ") AS user_counts;")
    HashMap<String, Object> findUvTypeCntByShortLink(@Param("param") ShortLinkStatsReqDTO requestParam);

    /**
     * 获取用户信息以判断是否新老访客
     * Case：创建时间在查询范围内，就是新用户。否则就是老用户
     */
    @Select(" <script>" +
            "SELECT " +
            "   USER, " +
            "   CASE " +
            "      WHEN MIN(create_time) BETWEEN STR_TO_DATE(CONCAT(#{startDate}, ' 00:00:00'), '%Y-%m-%d %H:%i:%s') " +
            "      AND STR_TO_DATE(CONCAT(#{endDate}, ' 23:59:59'), '%Y-%m-%d %H:%i:%s') THEN '新访客' " +
            "      ELSE '老访客' " +
            "   END AS uvType " +
            "FROM " +
            "   t_link_access_logs " +
            "WHERE " +
            "   full_short_url = #{fullShortUrl} " +
            "   AND gid = #{gid} " +
            "   AND user IN " +
            "   <foreach item='item' index='index' collection='userAccessLogsList' open='(' separator=',' close=')'> " +
            "      #{item} " +
            "   </foreach> " +
            "GROUP BY " +
            "   user " +
            "</script>")
    List<Map<String, Object>> selectUvTypeByUsers(
            @Param("gid") String gid,
            @Param("fullShortUrl") String fullShortUrl,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("userAccessLogsList") List<String> userAccessLogsList);
}
