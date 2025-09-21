package com.charagol.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.charagol.shortlink.project.common.convention.exception.ServiceException;
import com.charagol.shortlink.project.common.enums.ValiDateTypeEnum;
import com.charagol.shortlink.project.dao.entity.ShortLinkDO;
import com.charagol.shortlink.project.dao.entity.ShortLinkGotoDO;
import com.charagol.shortlink.project.dao.mapper.ShortLinkGotoMapper;
import com.charagol.shortlink.project.dao.mapper.ShortLinkMapper;
import com.charagol.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.charagol.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.charagol.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.charagol.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.charagol.shortlink.project.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.charagol.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.charagol.shortlink.project.service.ShortLinkService;
import com.charagol.shortlink.project.toolkit.HashUtil;
import com.charagol.shortlink.project.toolkit.LinkUtil;
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
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.charagol.shortlink.project.common.constant.RedisKeyConstant.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;


    /**
     * 创建短链接
     * @param requestParam 创建短链接请求参数
     * @return 短链接创建信息
     */
    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        // 1. 生成短链后缀
        String shortLinkSuffix = generateSuffix(requestParam);
        // 2. 拼接完整短链 URL
        String fullShortUrl = StrBuilder.create(requestParam.getDomain())
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
        // 6. 将完整链接加入布隆过滤器，避免频繁重复生成（不用后缀：一个后缀可能在多个域名中使用）
        stringRedisTemplate.opsForValue().set(
                String.format(GOTO_SHORT_LINK_KEY, fullShortUrl),
                requestParam.getOriginUrl(),
                LinkUtil.getLinkCacheValidTime(requestParam.getValidDate()),TimeUnit.MILLISECONDS
        );
        shortUriCreateCachePenetrationBloomFilter.add(fullShortUrl);

        // 7. 返回 DTO：从ShortLinkDO中取值，组装成返回值
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl(shortLinkDO.getFullShortUrl())
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .build();
    }

    /**
     * 修改短链接
     * @param requestParam 请求参数
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        // 设定有效期的时候，如果是空的，需要额外判断
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getDelFlag, 0)
                .eq(ShortLinkDO::getEnableStatus, 0);
        ShortLinkDO hasShortLinkDO = baseMapper.selectOne(queryWrapper);
        if (hasShortLinkDO == null) {
            throw new ServiceException("短链接不存在");
        }
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(hasShortLinkDO.getDomain())
                .shortUri(hasShortLinkDO.getShortUri())
                .clickNum(hasShortLinkDO.getClickNum())
                .favicon(hasShortLinkDO.getFavicon())
                .createdType(hasShortLinkDO.getCreatedType())
                .gid(requestParam.getGid())                     // 用哪个都行
                .originUrl(requestParam.getOriginUrl())
                .describe(requestParam.getDescribe())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .build();
        if (Objects.equals(hasShortLinkDO.getGid(), requestParam.getGid())){
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0)
                    .set(Objects.equals(requestParam.getValidDateType(), ValiDateTypeEnum.PERMANENT.getType()), ShortLinkDO::getValidDate, null);
            baseMapper.update(shortLinkDO,updateWrapper);
        }else{
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, hasShortLinkDO.getGid())    // 这里如果不一致，需要从hasShortLinkDO中获取gid
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0);
            baseMapper.delete(updateWrapper);
            shortLinkDO.setGid(requestParam.getGid());
            baseMapper.insert(shortLinkDO);
        }
        // 传递的分组与原来的分组不匹配，则分组发生更改，需要先删除
    }


    /**
     * 分页查询短链
     * @param requestParam 分页查询请求参数
     * @return 分页查询结果
     */
    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus, 0)
                .eq(ShortLinkDO::getDelFlag, 0);

        // 1. selectPage传入参数包括requestParam，其中封装了size、current等分页信息，相当于指定limit
        // 2. queryWrapper是查询条件，等价于：WHERE gid = '15' AND enable_status = 0 AND del_flag = 0。
        IPage<ShortLinkDO> resultPage = baseMapper.selectPage(requestParam, queryWrapper);

        // resultPage继承自Ipage，具有convert方法，可以将查询结果转换为指定类型
        return resultPage.convert(each -> BeanUtil.toBean(each, ShortLinkPageRespDTO.class));
    }

    @Override
    public List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> requestParam) {
        QueryWrapper<ShortLinkDO> queryWrapper = Wrappers.query(new ShortLinkDO())
                .select("gid, count(*) as shortLinkCount")
                .in("gid", requestParam)
                .eq("enable_status", 0)
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
        String fullShortUrl = request.getScheme()+ "://" + serverName + "/" + shortUri;

        // 第一次检查：尝试从 Redis 中获取原始链接
        String originalLink = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
        if (StrUtil.isNotBlank(originalLink)) {
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

            if (shortLinkDO != null) {
                if (shortLinkDO.getValidDate() != null && shortLinkDO.getValidDate().before(new Date())) {
                    stringRedisTemplate.opsForValue().set(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl),"-",30, TimeUnit.SECONDS);
                    response.sendRedirect("/page/notfound");
                    return;
                }
                stringRedisTemplate.opsForValue().set(
                        String.format(GOTO_SHORT_LINK_KEY, fullShortUrl),
                        shortLinkDO.getOriginUrl(),
                        LinkUtil.getLinkCacheValidTime(shortLinkDO.getValidDate()),TimeUnit.MILLISECONDS
                );
                response.sendRedirect(shortLinkDO.getOriginUrl());
            }
        } finally {
            lock.unlock();
        }
    }




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
            String candidate = requestParam.getDomain() + "/" + shortUri;
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
}
