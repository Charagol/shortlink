package com.charagol.shortlink.project.handler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.charagol.shortlink.project.common.convention.result.Result;
import com.charagol.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.charagol.shortlink.project.dto.resp.ShortLinkCreateRespDTO;

/**
 * 自定义流控策略
 * 这个类包含了当Sentinel触发限流或降级时的处理逻辑（兜底方法）
 */
public class CustomBlockHandler {

    /**
     * 当 "create_short-link" 资源被Sentinel限流或降级时，会调用这个方法。
     *
     * @param requestParam 原始请求参数
     * @param exception Sentinel抛出的限流或降级异常
     * @return 返回一个统一的错误结果
     */
    public static Result<ShortLinkCreateRespDTO> createShortLinkBlockHandlerMethod(ShortLinkCreateReqDTO requestParam, BlockException exception) {
        return new Result<ShortLinkCreateRespDTO>().setCode("B100000").setMessage("当前访问网站人数过多，请稍后再试...");
    }

    /** NOTE
     *   注意：
     *   1. 方法必须是 public static。
     *   2. 方法的参数列表需要与被保护的方法（ShortLinkController#createShortLink）的参数列表一致，
     *      并且在最后额外增加一个 BlockException 类型的参数。
     *      例如：
     *        原方法 createShortLink `(ShortLinkCreateReqDTO requestParam)`，
     *        则 blockHandler 方法为 `(ShortLinkCreateReqDTO requestParam, BlockException exception)`。
     *   3. 方法的返回值类型需要与被保护的方法的返回值类型一致。
     */
}