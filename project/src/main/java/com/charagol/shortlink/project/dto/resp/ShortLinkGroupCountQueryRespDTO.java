package com.charagol.shortlink.project.dto.resp;

import lombok.Data;

@Data
public class ShortLinkGroupCountQueryRespDTO {

    /**
     * 分组ID
     */
    private String gid;

    /**
     * 短链接数量
     */
    private  Integer shortLinkCount;

}
