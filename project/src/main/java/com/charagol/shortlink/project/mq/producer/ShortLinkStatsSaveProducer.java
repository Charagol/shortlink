package com.charagol.shortlink.project.mq.producer;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 短链接监控状态保存消息队列生产者
 */
@Component
@RequiredArgsConstructor
public class ShortLinkStatsSaveProducer {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${spring.data.redis.channel-topic.short-link-stats}")
    private String topic;

    /**
     * 发送短链接统计消息到 Redis Stream
     *
     * @param producerMap 包含统计数据的 Map，会作为消息内容写入 Stream
     */
    public void send(Map<String, String> producerMap) {
        // 使用 StringRedisTemplate 的 Stream 操作，将 producerMap 添加到指定的 topic (Stream)
        // Redis Stream 会为每条消息自动生成一个唯一的ID
        stringRedisTemplate.opsForStream().add(topic, producerMap);
    }
}