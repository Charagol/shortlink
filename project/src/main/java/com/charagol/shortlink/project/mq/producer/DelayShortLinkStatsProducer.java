package com.charagol.shortlink.project.mq.producer;


import cn.hutool.core.lang.UUID;
import com.charagol.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.charagol.shortlink.project.common.constant.RedisKeyConstant.DELAY_QUEUE_STATS_KEY;

/**
 * 延迟消费短链接统计发送者
 */
@Component
@RequiredArgsConstructor
public class DelayShortLinkStatsProducer {

    private final RedissonClient redissonClient;

    /**
     * 发送延迟消费短链接统计
     *
     * @param statsRecord 短链接统计实体参数
     */
    public void send(ShortLinkStatsRecordDTO statsRecord) {
        /** NOTE
         *   1. 在数据传输实体类中添加一个Key字段
         *   2. 生产者中为key赋值
         *   3. 消费者中根据key判断消息是否处理过
         *   4. 整体实现对原有的数据方法的 0 侵入
         */
        statsRecord.setKeys(UUID.fastUUID().toString());

        // 1. 获取一个Redisson的阻塞双端队列RBlockingDeque，拥有唯一标识
        // blockingDeque 为延迟队列内部实际“到期消息”的接收队列
        RBlockingDeque<ShortLinkStatsRecordDTO> blockingDeque = redissonClient.getBlockingDeque(DELAY_QUEUE_STATS_KEY);

        // 2. 基于到期接受队列，获取对应的延迟队列实例——消息到期后，自动转入到期队列
        RDelayedQueue<ShortLinkStatsRecordDTO> delayedQueue = redissonClient.getDelayedQueue(blockingDeque);

        // 3. 将统计记录statsRecord放入延迟队列，并设置5秒的延迟时间。5秒后自动转入到期队列
        // 延迟队列数据结构：Sorted Set{Value,Score}。将statsRecord序列化放入值，超时时间戳放入Score，并存在周期性守护线程扫描到期消息
        delayedQueue.offer(statsRecord, 5, TimeUnit.SECONDS);
    }
}