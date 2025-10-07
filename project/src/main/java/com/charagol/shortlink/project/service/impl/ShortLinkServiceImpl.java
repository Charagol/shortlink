package com.charagol.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.Week;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charagol.shortlink.project.common.convention.exception.ClientException;
import com.charagol.shortlink.project.common.convention.exception.ServiceException;
import com.charagol.shortlink.project.common.enums.ValiDateTypeEnum;
import com.charagol.shortlink.project.config.GotoDomainWhiteListConfiguration;
import com.charagol.shortlink.project.dao.entity.*;
import com.charagol.shortlink.project.dao.mapper.*;
import com.charagol.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.charagol.shortlink.project.dto.req.ShortLinkBatchCreateReqDTO;
import com.charagol.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.charagol.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.charagol.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.charagol.shortlink.project.dto.resp.*;
import com.charagol.shortlink.project.mq.producer.DelayShortLinkStatsProducer;
import com.charagol.shortlink.project.service.LinkStatsTodayService;
import com.charagol.shortlink.project.service.ShortLinkService;
import com.charagol.shortlink.project.toolkit.HashUtil;
import com.charagol.shortlink.project.toolkit.LinkUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.charagol.shortlink.project.common.constant.RedisKeyConstant.*;
import static com.charagol.shortlink.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final LinkStatsTodayMapper linkStatsTodayMapper;
    private final LinkStatsTodayService linkStatsTodayService;
    private final DelayShortLinkStatsProducer delayShortLinkStatsProducer;
    private final GotoDomainWhiteListConfiguration gotoDomainWhiteListConfiguration;


    @Value("${short-link.stats.locale.amap-key}")  // 使用springbootframword.bean的方式注入
    private String statsLocaleAmapKey;

    @Value("${short-link.domain.default}")
    private String createShortLinkDefaultDomain;

    /**
     * 创建短链接
     * @param requestParam 创建短链接请求参数
     * @return 短链接创建信息
     */
    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        verificationWhitelist(requestParam.getOriginUrl());
        // 1. 生成短链后缀
        String shortLinkSuffix = generateSuffix(requestParam);
        // 2. 拼接完整短链 URL
        String fullShortUrl = StrBuilder.create(createShortLinkDefaultDomain)
                .append("/")
                .append(shortLinkSuffix)
                .toString();
        // 3. 构建要持久化的实体
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(requestParam.getDomain())
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .createdType(requestParam.getCreatedType())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .describe(requestParam.getDescribe())
                .shortUri(shortLinkSuffix)
                .enableStatus(0)
                .totalPv(0)
                .totalUv(0)
                .totalUip(0)
                .delTime(0L)    // 在创建时，默认无删除时间
                .fullShortUrl(fullShortUrl)
                .favicon(getFavicon(requestParam.getOriginUrl()))
                .build();
        ShortLinkGotoDO linkGotoDO = ShortLinkGotoDO.builder()
                .fullShortUrl(fullShortUrl)
                .gid(requestParam.getGid())
                .build();
        try {
            // 4. 插入数据库（乐观）
            baseMapper.insert(shortLinkDO);
            // 4.5 在新建短链接时，插入短链接跳转表
            shortLinkGotoMapper.insert(linkGotoDO);
        } catch (DuplicateKeyException ex) {
            // 5. 遇到主键/唯一键冲突时再查一次，防止重复插入
            LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl);
            ShortLinkDO hasShortLinkDO = baseMapper.selectOne(queryWrapper);
            if (hasShortLinkDO != null) {
                log.warn("短链接：{} 重复入库", fullShortUrl);
                throw new ServiceException("短链接生成重复");
            }
            // 如果查不到，则抛出原异常
            throw ex;
        }
        // 6. 缓存预热：将完整链接加入布隆过滤器，避免频繁重复生成（不用后缀：一个后缀可能在多个域名中使用）
        stringRedisTemplate.opsForValue().set(
                String.format(GOTO_SHORT_LINK_KEY, fullShortUrl),
                requestParam.getOriginUrl(),
                LinkUtil.getLinkCacheValidTime(requestParam.getValidDate()),TimeUnit.MILLISECONDS
        );
        shortUriCreateCachePenetrationBloomFilter.add(fullShortUrl);

        // 7. 返回 DTO：从ShortLinkDO中取值，组装成返回值
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl("http://" + shortLinkDO.getFullShortUrl())
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .build();
    }

    /**
     * 批量创建短链接
     * @param requestParam 批量创建短链接请求参数
     * @return 批量创建短链接返回参数
     */
    @Override
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        List<String> originUrls = requestParam.getOriginUrls();
        List<String> describes = requestParam.getDescribes();
        List<ShortLinkBaseInfoRespDTO> result = new ArrayList<>();
        for (int i = 0; i < originUrls.size(); i++) {
            ShortLinkCreateReqDTO shortLinkCreateReqDTO = BeanUtil.toBean(requestParam, ShortLinkCreateReqDTO.class);
            shortLinkCreateReqDTO.setOriginUrl(originUrls.get(i));
            shortLinkCreateReqDTO.setDescribe(describes.get(i));
            try {
                ShortLinkCreateRespDTO shortLink = createShortLink(shortLinkCreateReqDTO);
                ShortLinkBaseInfoRespDTO linkBaseInfoRespDTO = ShortLinkBaseInfoRespDTO.builder()
                        .fullShortUrl(shortLink.getFullShortUrl())
                        .originUrl(shortLink.getOriginUrl())
                        .describe(describes.get(i))
                        .build();
                result.add(linkBaseInfoRespDTO);
            } catch (Throwable ex) {
                log.error("批量创建短链接失败，原始参数：{}", originUrls.get(i));
            }
        }
        return ShortLinkBatchCreateRespDTO.builder()
                .total(result.size())
                .baseLinkInfos(result)
                .build();
    }

    /**
     * 修改短链接
     * @param requestParam 请求参数
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        verificationWhitelist(requestParam.getOriginUrl());
        // 1. 根据请求参数查询现有短链接信息
        // 以原始Gid来查询数据库中的全部信息
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getOriginGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getDelFlag, 0)
                .eq(ShortLinkDO::getEnableStatus, 0);
        ShortLinkDO hasShortLinkDO = baseMapper.selectOne(queryWrapper);

        // 2. 如果不存在，抛出异常
        if (hasShortLinkDO == null) {
            throw new ServiceException("短链接不存在");
        }

        // 3. 判断分组ID（gid）是否发生变化
        if (Objects.equals(hasShortLinkDO.getGid(), requestParam.getGid())){
            // 4.1. 分组ID未变化：直接更新现有短链接记录
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .eq(ShortLinkDO::getDelFlag, 0)      // 构建更新条件：0=未删除
                    .eq(ShortLinkDO::getEnableStatus, 0)   // 构建更新条件：0=启用
                    .set(Objects.equals(requestParam.getValidDateType(), ValiDateTypeEnum.PERMANENT.getType()), ShortLinkDO::getValidDate, null);
            ShortLinkDO shortLinkDO = ShortLinkDO.builder()    // 构建更新实体
                    .domain(hasShortLinkDO.getDomain())
                    .shortUri(hasShortLinkDO.getShortUri())
                    .favicon(hasShortLinkDO.getFavicon())
                    .createdType(hasShortLinkDO.getCreatedType())
                    .gid(requestParam.getGid())                      // 部分从原来实体中获取，部分从请求参数中获取
                    .originUrl(requestParam.getOriginUrl())
                    .describe(requestParam.getDescribe())
                    .validDateType(requestParam.getValidDateType())
                    .validDate(requestParam.getValidDate())
                    .build();
            baseMapper.update(shortLinkDO,updateWrapper);
        }else{
            // 4.2. 分组ID已变化
            // 引入写锁：当需要写入操作的时候，不允许读
            RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, requestParam.getFullShortUrl()));
            RLock rLock = readWriteLock.writeLock();
            if (!rLock.tryLock()) {
                throw new ServiceException("短链接正在被访问，请稍后再试...");
            }
            try {
                // 4.2.1 软删除旧的短链接记录
                LambdaUpdateWrapper<ShortLinkDO> linkUpdateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                        .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                        .eq(ShortLinkDO::getGid, hasShortLinkDO.getGid())
                        .eq(ShortLinkDO::getDelFlag, 0)
                        .eq(ShortLinkDO::getDelTime, 0L)
                        .eq(ShortLinkDO::getEnableStatus, 0);
                ShortLinkDO delShortLinkDO = ShortLinkDO.builder()
                        .delTime(System.currentTimeMillis())         // 设置删除时间为当前时间戳
                        .build();
                delShortLinkDO.setDelFlag(1);                        // 设置删除标识为1（delFlag来自baseDO，不支持builder只能set）
                baseMapper.update(delShortLinkDO, linkUpdateWrapper);

                // 4.2.2 插入新的短链接记录（在新分组下）
                ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                        .domain(createShortLinkDefaultDomain)
                        .originUrl(requestParam.getOriginUrl())
                        .gid(requestParam.getGid())                 // 从请求参数中获取新分组GID
                        .createdType(hasShortLinkDO.getCreatedType())
                        .validDateType(requestParam.getValidDateType())
                        .validDate(requestParam.getValidDate())
                        .describe(requestParam.getDescribe())
                        .shortUri(hasShortLinkDO.getShortUri())
                        .enableStatus(hasShortLinkDO.getEnableStatus())
                        .totalPv(hasShortLinkDO.getTotalPv())
                        .totalUv(hasShortLinkDO.getTotalUv())
                        .totalUip(hasShortLinkDO.getTotalUip())
                        .fullShortUrl(hasShortLinkDO.getFullShortUrl())
                        .favicon(getFavicon(requestParam.getOriginUrl()))
                        .delTime(0L)
                        .build();
                baseMapper.insert(shortLinkDO);

                // 4.2.3 更新相关统计数据
                // (1) 更新每日统计数据 (LinkStatsTodayDO)
                // 构建查询条件，查找旧分组下的所有今日统计数据，封装在List中
                LambdaQueryWrapper<LinkStatsTodayDO> statsTodayQueryWrapper = Wrappers.lambdaQuery(LinkStatsTodayDO.class)
                        .eq(LinkStatsTodayDO::getFullShortUrl, requestParam.getFullShortUrl())
                        .eq(LinkStatsTodayDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkStatsTodayDO::getDelFlag, 0);
                List<LinkStatsTodayDO> linkStatsTodayDOList = linkStatsTodayMapper.selectList(statsTodayQueryWrapper);
                // 如果有，就批量删除然后改分组再插入。
                if (CollUtil.isNotEmpty(linkStatsTodayDOList)) {
                    linkStatsTodayMapper.deleteBatchIds(linkStatsTodayDOList.stream()
                            .map(LinkStatsTodayDO::getId)
                            .toList()
                    );
                    // 由于最初的linkStatsTodayDOList从数据库中查出的“所属GID下的全部条目全部数据”，更改GID重新插入即可。
                    linkStatsTodayDOList.forEach(each -> each.setGid(requestParam.getGid()));
                    linkStatsTodayService.saveBatch(linkStatsTodayDOList);
                }

                // (2) 更新短链接跳转表 (ShortLinkGotoDO)
                // NOTE Goto表中以fullShortUrl为分片键，储存了每个短链接的fullShortUrl和Gid对应关系。因此只需要查到这个短链接属于谁，然后更改再插入即可。
                LambdaQueryWrapper<ShortLinkGotoDO> linkGotoQueryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                        .eq(ShortLinkGotoDO::getFullShortUrl, requestParam.getFullShortUrl())
                        .eq(ShortLinkGotoDO::getGid, hasShortLinkDO.getGid());
                ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(linkGotoQueryWrapper);
                shortLinkGotoMapper.deleteById(shortLinkGotoDO.getId());                        // 删除旧的跳转记录
                shortLinkGotoDO.setGid(requestParam.getGid());                                  // 将跳转记录的GID更新为新GID
                shortLinkGotoMapper.insert(shortLinkGotoDO);                                    // 插入新的跳转记录

                //更新访问统计数据：——通用逻辑：直接更新这些统计表中的 `gid` 字段
                // 访问统计
                LambdaUpdateWrapper<LinkAccessStatsDO> linkAccessStatsUpdateWrapper = Wrappers.lambdaUpdate(LinkAccessStatsDO.class)
                        .eq(LinkAccessStatsDO::getFullShortUrl, requestParam.getFullShortUrl())
                        .eq(LinkAccessStatsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkAccessStatsDO::getDelFlag, 0);
                LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                        .gid(requestParam.getGid())
                        .build();
                linkAccessStatsMapper.update(linkAccessStatsDO, linkAccessStatsUpdateWrapper);
                // 地域统计
                LambdaUpdateWrapper<LinkLocaleStatsDO> linkLocaleStatsUpdateWrapper = Wrappers.lambdaUpdate(LinkLocaleStatsDO.class)
                        .eq(LinkLocaleStatsDO::getFullShortUrl, requestParam.getFullShortUrl())
                        .eq(LinkLocaleStatsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkLocaleStatsDO::getDelFlag, 0);
                LinkLocaleStatsDO linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                        .gid(requestParam.getGid())
                        .build();
                linkLocaleStatsMapper.update(linkLocaleStatsDO, linkLocaleStatsUpdateWrapper);
                // 操作系统统计
                LambdaUpdateWrapper<LinkOsStatsDO> linkOsStatsUpdateWrapper = Wrappers.lambdaUpdate(LinkOsStatsDO.class)
                        .eq(LinkOsStatsDO::getFullShortUrl, requestParam.getFullShortUrl())
                        .eq(LinkOsStatsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkOsStatsDO::getDelFlag, 0);
                LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                        .gid(requestParam.getGid())
                        .build();
                linkOsStatsMapper.update(linkOsStatsDO, linkOsStatsUpdateWrapper);
                // 浏览器统计
                LambdaUpdateWrapper<LinkBrowserStatsDO> linkBrowserStatsUpdateWrapper = Wrappers.lambdaUpdate(LinkBrowserStatsDO.class)
                        .eq(LinkBrowserStatsDO::getFullShortUrl, requestParam.getFullShortUrl())
                        .eq(LinkBrowserStatsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkBrowserStatsDO::getDelFlag, 0);
                LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                        .gid(requestParam.getGid())
                        .build();
                linkBrowserStatsMapper.update(linkBrowserStatsDO, linkBrowserStatsUpdateWrapper);
                // 设备统计
                LambdaUpdateWrapper<LinkDeviceStatsDO> linkDeviceStatsUpdateWrapper = Wrappers.lambdaUpdate(LinkDeviceStatsDO.class)
                        .eq(LinkDeviceStatsDO::getFullShortUrl, requestParam.getFullShortUrl())
                        .eq(LinkDeviceStatsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkDeviceStatsDO::getDelFlag, 0);
                LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                        .gid(requestParam.getGid())
                        .build();
                linkDeviceStatsMapper.update(linkDeviceStatsDO, linkDeviceStatsUpdateWrapper);
                // 网络统计
                LambdaUpdateWrapper<LinkNetworkStatsDO> linkNetworkStatsUpdateWrapper = Wrappers.lambdaUpdate(LinkNetworkStatsDO.class)
                        .eq(LinkNetworkStatsDO::getFullShortUrl, requestParam.getFullShortUrl())
                        .eq(LinkNetworkStatsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkNetworkStatsDO::getDelFlag, 0);
                LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                        .gid(requestParam.getGid())
                        .build();
                linkNetworkStatsMapper.update(linkNetworkStatsDO, linkNetworkStatsUpdateWrapper);
                // 访问日志统计
                LambdaUpdateWrapper<LinkAccessLogsDO> linkAccessLogsUpdateWrapper = Wrappers.lambdaUpdate(LinkAccessLogsDO.class)
                        .eq(LinkAccessLogsDO::getFullShortUrl, requestParam.getFullShortUrl())
                        .eq(LinkAccessLogsDO::getGid, hasShortLinkDO.getGid())
                        .eq(LinkAccessLogsDO::getDelFlag, 0);
                LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                        .gid(requestParam.getGid())
                        .build();
                linkAccessLogsMapper.update(linkAccessLogsDO, linkAccessLogsUpdateWrapper);
            } finally {
                rLock.unlock();
            }
        }

        // 5. 缓存失效：清除与该短链接相关的Redis缓存
        // 只要对短链接进行了任何可能影响跳转的修改，都需要清除redis主缓存
        stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
        // 同时删除空值缓存，防止因旧的“无效”标记导致新生效的链接暂时无法访问。
        stringRedisTemplate.delete(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, requestParam.getFullShortUrl()));


        /**
         * 马哥的代码：尝试在更精准的条件下删除缓存，即只有当有效期发生变化时才去操作缓存。
        if (!Objects.equals(hasShortLinkDO.getValidDateType(), requestParam.getValidDateType())
                || !Objects.equals(hasShortLinkDO.getValidDate(), requestParam.getValidDate())) {
            // 只有有效期类型或有效期日期发生变化时才执行
            stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY, requestParam.getFullShortUrl()));

            // 针对特定情况：如果旧链接已过期，但现在被修改为永久或新的有效日期，则删除空值缓存
            if (hasShortLinkDO.getValidDate() != null && hasShortLinkDO.getValidDate().before(new Date())) {
                if (Objects.equals(requestParam.getValidDateType(), ValiDateTypeEnum.PERMANENT.getType()) || requestParam.getValidDate().after(new Date())) {
                    stringRedisTemplate.delete(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
                }
            }
        }
         */
    }


    /**
     * 分页查询短链
     * @param requestParam 分页查询请求参数
     * @return 分页查询结果
     */
    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        // NOTE
        //  调用Mybatis结合xml的传统查询来实现。
        //  内部实现了联查。目的为：获取短链接列表及其聚合统计。 而今日聚合数据都在t_link_today中。类merge添加字段为最优解
        //  LEFT JOIN能实现单次数据库访问，且结果映射简单。如果分查需要查n+1次
        IPage<ShortLinkDO> resultPage = baseMapper.pageLink(requestParam);

        // resultPage继承自Ipage，具有convert方法，可以将查询结果转换为指定类型
        return resultPage.convert(each -> BeanUtil.toBean(each, ShortLinkPageRespDTO.class));
    }


    /**
     * 根据gid查询短链接数量
     * @param requestParam 传入gid的列表
     * @return 短链接数量实体列表
     */
    @Override
    public List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> requestParam) {
        QueryWrapper<ShortLinkDO> queryWrapper = Wrappers.query(new ShortLinkDO())
                .select("gid, count(*) as shortLinkCount")
                .in("gid", requestParam)
                .eq("enable_status", 0)
                .eq("del_flag", 0)
                .eq("del_time", 0L)
                .groupBy("gid");

        List<Map<String, Object>> shortLinkDOList = baseMapper.selectMaps(queryWrapper);
        return BeanUtil.copyToList(shortLinkDOList, ShortLinkGroupCountQueryRespDTO.class);
    }


    /**
     * 重定向短链接
     * @param shortUri 短链接后缀
     * @param request HTTP请求
     * @param response HTTP响应
     */
    @SneakyThrows
    @Override
    public void restoreUrl(String shortUri, HttpServletRequest request, HttpServletResponse response) {
        String serverName = request.getServerName();
        // NOTE 在yaml中设置指定端口。如果端口指定了就拼接，没指定（默认80）就不管
        String serverPort = Optional.of(request.getServerPort())
                .filter(each -> !Objects.equals(each, 80))
                .map(String::valueOf)
                .map(each -> ":" + each)
                .orElse("");
        String fullShortUrl = serverName + serverPort + "/" + shortUri;

        // 第一次检查：尝试从 Redis 中获取原始链接
        String originalLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
        if (StrUtil.isNotBlank(originalLink)) {
            ShortLinkStatsRecordDTO statsRecord = buildLinkStatsRecordAndSetUser(fullShortUrl, request, response);
            shortLinkStats(fullShortUrl, null, statsRecord);
            response.sendRedirect(originalLink);
            return;
        }
        boolean contains = shortUriCreateCachePenetrationBloomFilter.contains(fullShortUrl);
        if (!contains) {
            response.sendRedirect("/page/notfound");
            return;
        }
        String gotoIsNullShortLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl));
        if (StrUtil.isNotBlank(gotoIsNullShortLink)) {
            response.sendRedirect("/page/notfound");
            return;
        }

        // 缓存未命中，准备查询数据库。
        RLock lock = redissonClient.getLock(String.format(LOCK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        lock.lock();
        try {
            // 第二次检查：获取锁之后，再次检查缓存
            // 因为可能在等待锁的过程中，其他线程已经查询完数据库并回填了缓存
            originalLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
            if (StrUtil.isNotBlank(originalLink)) {
                ShortLinkStatsRecordDTO statsRecord = buildLinkStatsRecordAndSetUser(fullShortUrl, request, response);
                shortLinkStats(fullShortUrl, null, statsRecord);
                response.sendRedirect(originalLink);
                return;
            }
            // 开始查询数据库
            LambdaQueryWrapper<ShortLinkGotoDO> linkGotoQueryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                    .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(linkGotoQueryWrapper);
            if (shortLinkGotoDO == null) {
                stringRedisTemplate.opsForValue().set(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl),"-",30, TimeUnit.SECONDS);
                response.sendRedirect("/page/notfound");
                // 严谨来说，此处需要进行封控
                return;
            }
            LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getGid, shortLinkGotoDO.getGid())
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0);
            ShortLinkDO shortLinkDO = baseMapper.selectOne(queryWrapper);

            // 被优化： if (shortLinkDO == null || shortLinkDO.getValidDate().before(new Date()) ) {}
            if (shortLinkDO == null || (shortLinkDO.getValidDate() != null && shortLinkDO.getValidDate().before(new Date()))){
                stringRedisTemplate.opsForValue().set(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl),"-",30, TimeUnit.SECONDS);
                response.sendRedirect("/page/notfound");
                return;
            }
            stringRedisTemplate.opsForValue().set(
                    String.format(GOTO_SHORT_LINK_KEY, fullShortUrl),
                    shortLinkDO.getOriginUrl(),
                    LinkUtil.getLinkCacheValidTime(shortLinkDO.getValidDate()),TimeUnit.MILLISECONDS
            );
            ShortLinkStatsRecordDTO statsRecord = buildLinkStatsRecordAndSetUser(fullShortUrl, request, response);
            shortLinkStats(fullShortUrl, shortLinkDO.getGid(), statsRecord);
            response.sendRedirect(shortLinkDO.getOriginUrl());
        } finally {
            lock.unlock();
        }
    }

    /* NOTE shortLinkStats 拆分笔记
     *  1. 原来的shortLinkStats负责[从HTTP请求中提取原始访问数据，处理UV\PV\UIP等、构建各种统计实体、持久化写入、锁机制] 等多个部分，职责过重，过耦合
     *  2. 简而言之，又得“挖”，又得“算”，又得“存”
     *  3. 引入 statsRecordDTO —— 临时的数据传输对象，将所有需要统计的信息打包起来。
     *  4. 提取 buildLinkStatsRecordAndSetUser 方法，将与 Http 交互的逻辑进行打包。负责从请求中解析出所有必要的统计信息，并封装打包到 statsRecordDTO
     *  5. 现在步骤变为，先调用 buildLinkStatsRecordAndSetUser 方法，获取 statsRecordDTO 然后返给 shortLinkStats 方法
     *  6. shortLinkStats 用于将 statsRecord 中的统计信息持久化到数据库，需获取【读锁】，若成功，便写入
     */

    /**
     * 统计短链接访问数据，每次访问-跳转都会记录这次的各种信息
     * 从信息中构建实体，最后写入对应表中。配合restoreUrl实现。
     *
     * @param fullShortUrl 完整短链接
     * @param request 请求信息
     * @param response 响应信息
     */
    private ShortLinkStatsRecordDTO buildLinkStatsRecordAndSetUser(String fullShortUrl, HttpServletRequest request, HttpServletResponse response) {
        AtomicBoolean uvFirstFlag = new AtomicBoolean();
        Cookie[] cookies = request.getCookies();
        AtomicReference<String> uv = new AtomicReference<>();

        String today = DateUtil.today();               // 给redis用的。注意：此时设置的为redis=key中加入当天时间。当天跨小时之后，uv等取值还是会为0（看统计量纲）
        // uv处理逻辑
        // 1. 新用户处理逻辑：
        Runnable addResponseCookieTask = () -> {
            uv.set(UUID.fastUUID().toString());
            Cookie uvCookie = new Cookie("uv", uv.get());   // key:uv  value:UUID
            uvCookie.setMaxAge(60 * 60 * 24 * 30);
            // 原版：uvCookie.setPath(StrUtil.sub(fullShortUrl, fullShortUrl.indexOf("/"), fullShortUrl.length()));
            try {
                uvCookie.setPath(new URI(fullShortUrl).getPath());   // 如果fullShortUrl带了域名，则不能用上面的方法。
            } catch (URISyntaxException e) {
                throw new RuntimeException("Failed to set UV cookie path due to URI syntax error", e);
            }
            response.addCookie(uvCookie);
            uvFirstFlag.set(Boolean.TRUE);
            String uvRedisKey = "short-link:stats:uv:" + fullShortUrl + ":" + today;
            stringRedisTemplate.opsForSet().add(uvRedisKey, uv.get());
            stringRedisTemplate.expire(uvRedisKey, 26, TimeUnit.HOURS); // 26小时过期，确保次日凌晨失效
        };
        // 2. 判断 cookie 是否为空
        if (ArrayUtil.isNotEmpty(cookies)) {
            // 2.1 不为空：老用户——遍历cookie，取出以 uv 为 key 的 cookie
            Arrays.stream(cookies)
                    .filter(each -> Objects.equals(each.getName(), "uv"))
                    .findFirst()
                    .map(Cookie::getValue)
                    .ifPresentOrElse(each -> {
                        uv.set(each);
                        // 2.1.1 如果有key为uv，取出值:UUID, 判断是否存在于Redis中
                        String uvRedisKey = "short-link:stats:uv:" + fullShortUrl + ":" + today;
                        Long uvAdded = stringRedisTemplate.opsForSet().add(uvRedisKey, each);
                        uvFirstFlag.set(uvAdded != null && uvAdded > 0L);
                        if (uvFirstFlag.get()) {                               // 如果是新UV才需要刷新过期时间
                            stringRedisTemplate.expire(uvRedisKey, 26, TimeUnit.HOURS);
                        }
                    }
                    // 2.1.2 如果没有key为uv的cookie，说明是新用户，走新用户Runnable
                    , addResponseCookieTask);
        } else {
            // 2.2 为空：新用户，走新用户Runnable
            addResponseCookieTask.run();
        }
        String remoteAddr = LinkUtil.getIp(request);
        String os = LinkUtil.getOs(request);
        String browser = LinkUtil.getBrowser(request);
        String device = LinkUtil.getDevice(request);
        String network = LinkUtil.getNetwork(request);
        // 修改 UIP Redis Key，添加 :today 后缀并设置过期时间
        String uipRedisKey  = "short-link:stats:uip:" + fullShortUrl + ":" + today;
        Long uipAdded = stringRedisTemplate.opsForSet().add(uipRedisKey, remoteAddr);
        boolean uipFirstFlag = uipAdded != null && uipAdded > 0L;
        if (uipFirstFlag) { // 如果是新UIP才需要刷新过期时间
            stringRedisTemplate.expire(uipRedisKey, 26, TimeUnit.HOURS);
        }
        return ShortLinkStatsRecordDTO.builder()
                .fullShortUrl(fullShortUrl)
                .uv(uv.get())
                .uvFirstFlag(uvFirstFlag.get())
                .uipFirstFlag(uipFirstFlag)
                .remoteAddr(remoteAddr)
                .os(os)
                .browser(browser)
                .device(device)
                .network(network)
                .build();
    }

    /* NOTE
    *   1. 用户请求 -> Controller -> Service.restoreUrl ->  Service.build...（统计数据） -> Service.shortLinkStats(尝试拿锁)
    *   2. 如果此时写锁被 Service.update 持有。if (!rLock.tryLock()) 会判中，由 Producer.send 记录，而后直接 return (持久化本来也不需要返回)
    *   3. 此时，待持久化的所有数据，都以参数传给了 Producer.send 方法。而用户正常重定向
    *   4. 本次统计参数 statsRecord 以被放入延迟队列。已序列化并存储在 Redis 的 Sorted Set 中，score 是 5 秒后的时间戳（此时尚未被写入）
    *   5. 消费队列获取相同延迟队列与到期队列，扫描到已经到期的数据，就进行持久化。
    */

    @Override
    public void shortLinkStats(String fullShortUrl, String gid, ShortLinkStatsRecordDTO statsRecord) {
        fullShortUrl = Optional.ofNullable(fullShortUrl).orElse(statsRecord.getFullShortUrl());
        // 引入读锁，当需要读取操作的时候，谁都可以读取
        // NOTE 读写锁中，读锁为共享锁，多个线程可以同时获取读锁；写锁为独占锁，只有一个线程可以获取写锁
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, fullShortUrl));
        RLock rLock = readWriteLock.readLock();
        if (!rLock.tryLock()) {
            delayShortLinkStatsProducer.send(statsRecord);
            return;
        }
        Date currentDate = new Date();    // 给获取当前时分秒用的
        try {
            // 一、获取Gid。前端没传gid就自己去拿
            if (StrUtil.isBlank(gid)){
                LambdaQueryWrapper<ShortLinkGotoDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                        .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
                ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(queryWrapper);
                gid = shortLinkGotoDO.getGid();
            }

            // 二、基础访问监控数据
            // 获取当前时间信息并写入数据库
            int hour = DateUtil.hour(currentDate, true);
            Week week = DateUtil.dayOfWeekEnum(currentDate);
            int weekValue = week.getIso8601Value();
            LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                    .pv(1)
                    .uv(statsRecord.getUvFirstFlag() ? 1 : 0)
                    .uip(statsRecord.getUipFirstFlag() ? 1 : 0)
                    .hour(hour)
                    .weekday(weekValue)
                    .fullShortUrl(fullShortUrl)
                    .gid(gid)
                    .date(currentDate)
                    .build();
            linkAccessStatsMapper.shortLinkStats(linkAccessStatsDO);

            // 三、 地区统计
            Map<String, Object> localeParamMap = new HashMap<>();
            localeParamMap.put("key", statsLocaleAmapKey);
            localeParamMap.put("ip", statsRecord.getRemoteAddr());
            String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, localeParamMap);
            JSONObject localeResultObj = JSON.parseObject(localeResultStr);
            String infoCode = localeResultObj.getString("infocode");
            String actualProvince = "未知";
            String actualCity = "未知";
            if (StrUtil.isNotBlank(infoCode) && StrUtil.equals(infoCode, "10000")) {
                String province = localeResultObj.getString("province");
                boolean unknownFlag = StrUtil.equals(province,"[]");
                LinkLocaleStatsDO linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                        .province(actualProvince = unknownFlag ? "未知" : province)
                        .city(actualCity = unknownFlag ? "未知" : localeResultObj.getString("city"))
                        .adcode(unknownFlag ? "未知" : localeResultObj.getString("adcode"))
                        .cnt(1)
                        .fullShortUrl(fullShortUrl)
                        .country("中国")
                        .gid(gid)
                        .date(currentDate)
                        .build();
                linkLocaleStatsMapper.shortLinkLocaleState(linkLocaleStatsDO);
            }

            // 四、 os统计
            LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                    .os(statsRecord.getOs())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .gid(gid)
                    .date(currentDate)
                    .build();
            linkOsStatsMapper.shortLinkOsState(linkOsStatsDO);

            // 五、浏览器统计
            LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                    .browser(statsRecord.getBrowser())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .gid(gid)
                    .date(currentDate)
                    .build();
            linkBrowserStatsMapper.shortLinkBrowserState(linkBrowserStatsDO);

            // 六、设备统计
            LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                    .device(statsRecord.getDevice())
                    .cnt(1)
                    .gid(gid)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkDeviceStatsMapper.shortLinkDeviceState(linkDeviceStatsDO);

            // 七、网络统计
            LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                    .network(statsRecord.getNetwork())
                    .cnt(1)
                    .gid(gid)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkNetworkStatsMapper.shortLinkNetworkState(linkNetworkStatsDO);

            // 八、日志统计
            LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                    .user(statsRecord.getUv())
                    .ip(statsRecord.getRemoteAddr())
                    .browser(statsRecord.getBrowser())
                    .network(statsRecord.getNetwork())
                    .device(statsRecord.getDevice())
                    .locale(StrUtil.join("-", "中国", actualProvince, actualCity))
                    .os(statsRecord.getOs())
                    .fullShortUrl(fullShortUrl)
                    .gid(gid)
                    .build();
            linkAccessLogsMapper.insert(linkAccessLogsDO);
            // 九、常用指标更新
            baseMapper.incrementStats(gid, fullShortUrl, 1, statsRecord.getUvFirstFlag() ? 1 : 0, statsRecord.getUipFirstFlag() ? 1 : 0);
            // 十、今日数据统计
            LinkStatsTodayDO linkStatsTodayDO = LinkStatsTodayDO.builder()
                    .todayPv(1)
                    .todayUv(statsRecord.getUvFirstFlag() ? 1 : 0)
                    .todayUip(statsRecord.getUipFirstFlag() ? 1 : 0)
                    .gid(gid)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkStatsTodayMapper.shortLinkTodayState(linkStatsTodayDO);
        } catch (Exception e) {
            log.error("shortLinkStats error", e);
        } finally {
            rLock.unlock();
        }
    }

    /**
     *  生成短链接后缀-工具方法
     * @param requestParam 短链接后缀生成请求参数
     * @return 短链接后缀
     */
    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        int customGenerateCount = 0;
        String shortUri;
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接频繁生成，请稍后再试");
            }
            // 将原始 URL 加上时间戳，保证每次 hash 值不同
            String originUrl = requestParam.getOriginUrl() + System.currentTimeMillis();
            // 转 Base62
            shortUri = HashUtil.hashToBase62(originUrl);
            // 判断布隆过滤器中是否已存在该后缀
            String candidate = createShortLinkDefaultDomain + "/" + shortUri;
            if (!shortUriCreateCachePenetrationBloomFilter.contains(candidate)) {
                break;
            }
            customGenerateCount++;
        }
        return shortUri;
    }

    /**
     * 获取网站的favicon图标链接
     *
     * @param url 网站的URL
     * @return favicon图标的链接，如果不存在则返回null
     */
    @SneakyThrows
    private String getFavicon(String url) {
        // 以给定的URL
        URL targetUrl = new URL(url);
        // 打开连接
        HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
        // 禁止自动重定向
        connection.setInstanceFollowRedirects(false);
        // 设置请求方式为GET
        connection.setRequestMethod("GET");
        // 连接
        connection.connect();

        // 获取响应码
        int responseCode = connection.getResponseCode();
        // 如果为重定向
        if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            // 获得重定向的URL
            String redirectUrl = connection.getHeaderField("Location");
            // 以重定向URL为主
            if (redirectUrl != null) {
                // 重新创建URL对象
                URL newUrl = new URL(redirectUrl);
                // 打开新的连接
                connection = (HttpURLConnection) newUrl.openConnection();
                // 设置请求方式为GET
                connection.setRequestMethod("GET");
                // 连接
                connection.connect();
                // 获取新的响应码
                responseCode = connection.getResponseCode();
            }
        }

        // 如果响应码为200 (HTTP_OK)
        if (responseCode == HttpURLConnection.HTTP_OK) {
            // 使用Jsoup库从指定URL获取文档对象
            Document document = Jsoup.connect(url).get();
            // 查找第一个匹配的<link>标签，属性值包含"shortcut"或"icon"
            Element faviconLink = document.select("link[rel~=(?i)^((shortcut )?icon)]").first();
            // 如果找到图标链接
            if (faviconLink != null) {
                // 返回图标链接的绝对路径
                return faviconLink.attr("abs:href");
            }
        }

        // 如果不存在favicon图标链接，则返回null
        return null;
    }

    private void verificationWhitelist(String originUrl) {
        Boolean enable = gotoDomainWhiteListConfiguration.getEnable();
        if (enable == null || !enable) {
            return;
        }
        String domain = LinkUtil.extractDomain(originUrl);
        if (StrUtil.isBlank(domain)) {
            throw new ClientException("跳转链接填写错误");
        }
        List<String> details = gotoDomainWhiteListConfiguration.getDetails();
        if (!details.contains(domain)) {
            throw new ClientException("演示环境为避免恶意攻击，请生成以下网站跳转链接：" + gotoDomainWhiteListConfiguration.getNames());
        }
    }
}
