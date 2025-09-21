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

package com.charagol.shortlink.project.service.impl;

import com.charagol.shortlink.project.service.UrlTitleService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * URL 标题接口实现层
 */
@Slf4j
@Service
public class UrlTitleServiceImpl implements UrlTitleService {

    @SneakyThrows
    @Override
    public String getTitleByUrl(String url){
        URL tagetUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) tagetUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            Document document = Jsoup.connect(url).get();
            log.info("8081:查询链接：{}，查得网站标题：{}", url, document.title());
            return document.title();
        }
        log.warn("8081:查询链接：{}，查询失败", url);
        return "Erro while fetching title";
    }
}
