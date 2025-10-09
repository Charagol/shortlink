package com.charagol.shortlink.project.mq.consumer;


import com.charagol.shortlink.project.common.convention.exception.ServiceException;
import com.charagol.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.charagol.shortlink.project.mq.idempotent.MessageQueueIdempotentHandler;
import com.charagol.shortlink.project.service.ShortLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

import static com.charagol.shortlink.project.common.constant.RedisKeyConstant.DELAY_QUEUE_STATS_KEY;

/**
 * 延迟记录短链接统计组件
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：link）获取项目资料
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelayShortLinkStatsConsumer implements InitializingBean {

    private final RedissonClient redissonClient;
    private final ShortLinkService shortLinkService;
    private final MessageQueueIdempotentHandler messageQueueIdempotentHandler;

    public void onMessage() {
        // 1. 创建一个单线程的执行器，用于异步消费消息
        Executors.newSingleThreadExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable);
                            thread.setName("delay_short-link_stats_consumer");
                            thread.setDaemon(Boolean.TRUE);
                            return thread;
                        })
                .execute(() -> {
                    // 2. 获取到期消息接收队列blockingDeque。由于标识唯一，与生产者中到期队列相同。 而后获取到期队列的延迟队列实例
                    RBlockingDeque<ShortLinkStatsRecordDTO> blockingDeque = redissonClient.getBlockingDeque(DELAY_QUEUE_STATS_KEY);
                    RDelayedQueue<ShortLinkStatsRecordDTO> delayedQueue = redissonClient.getDelayedQueue(blockingDeque);

                    // 3. 从延迟队列中取出消息，并进行处理（无限循环）。
                    for (; ; ) {
                        try {
                            // 尝试从延迟队列中取出一条消息。这里的poll()是从底层的blockingDeque中取已经到期的消息
                            ShortLinkStatsRecordDTO statsRecord = delayedQueue.poll();
                            if (statsRecord != null) {
                                // 3.1 有消息：调用短链接服务进行统计处理
                                if (!messageQueueIdempotentHandler.isMessageProcessed(statsRecord.getKeys())) {
                                    // 判断当前的这个消息流程是否执行完成
                                    if (messageQueueIdempotentHandler.isAccomplish(statsRecord.getKeys())) {
                                        return;
                                    }
                                    throw new ServiceException("消息未完成流程，需要消息队列重试");
                                }
                                try {
                                    shortLinkService.shortLinkStats(null, null, statsRecord);
                                } catch (Throwable ex) {
                                    messageQueueIdempotentHandler.delMessageProcessed(statsRecord.getKeys());
                                    log.error("延迟记录短链接监控消费异常", ex);
                                }
                                messageQueueIdempotentHandler.setAccomplish(statsRecord.getKeys());
                                continue;
                            }
                            // 3.2 没消息：当前线程暂停500毫秒，避免空轮询导致CPU过高
                            LockSupport.parkUntil(500);
                        } catch (Throwable ignored) {
                            // TODO 实际生产环境中，这里应记录日志并考虑消息重试或放入死信队列
                        }
                    }
                });
    }

    /**
     * Spring容器初始化完成后自动调用此方法
     * 这里调用onMessage()方法，启动消费者线程
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        onMessage();
    }
}