package com.tzp.myWebTest.controller;

import com.tzp.myWebTest.aop.EnableAsync;
import com.tzp.myWebTest.dto.EsQueryDTO;
import com.tzp.myWebTest.dto.MapCreateDTO;
import com.tzp.myWebTest.entity.BilibiliComment;
import com.tzp.myWebTest.service.AsyncService;
import com.tzp.myWebTest.service.BilibiliCommentService;
import com.tzp.myWebTest.service.EsDocumentService;
import com.tzp.myWebTest.service.EsIndexService;
import com.tzp.myWebTest.util.AsyncMsgUtil;
import com.tzp.myWebTest.util.AvidAndBvidUtil;
import com.tzp.myWebTest.util.MsgUtil;
import com.tzp.myWebTest.util.PageUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.ElasticsearchStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/biliComment")
@Api(value = "BilibiliCommentController", tags = "Bili的爬虫测试")
public class BilibiliCommentController {

    @Autowired
    private EsDocumentService<BilibiliComment> esDocumentService;

    @Autowired
    private EsIndexService esIndexService;

    @Autowired
    private AsyncService asyncService;

    @Autowired
    private BilibiliCommentService bilibiliCommentService;

    @ApiOperation("查询当前爬取进度")
    @GetMapping("/getAsyncMsg")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "当时返回的检索id", required = true, dataType = "string", paramType = "query", defaultValue = "")
    })
    public MsgUtil<Object> getAsyncMsg(String id) {
        if (StringUtils.isNoneBlank(id)) {
            AsyncMsgUtil asyncMsg = asyncService.findAsyncMsgUtil(id);
            return MsgUtil.success("请求成功", asyncMsg);
        } else {
            return MsgUtil.fail("请输入查询BV号以查询进度");
        }
    }

    @EnableAsync
    @ApiOperation("多线程添加")
    @PostMapping("/addDoc")
    public MsgUtil<Object> addNewDocumentByBathAndAsync(@RequestBody MapCreateDTO dto) {
        try {
            Map<String, String> map = bilibiliCommentService.convertMap(dto);
            if ( !"11".equals(dto.getType()) ) {
                String oid = String.valueOf(AvidAndBvidUtil.bvidToAid(dto.getOid()));
                map.put("oid", oid);
            }
            boolean result = esIndexService.indexExists(map.get("oid"));
            if (result) {
                return MsgUtil.fail(1,"本站有缓存：");
            }
            esIndexService.createIndex(map.get("oid"));
            bilibiliCommentService.addComment(map);
            return MsgUtil.success("添加成功");
        } catch (Exception e) {
            e.printStackTrace();
            String oid = "";
            if ( !"11".equals(dto.getType()) ) {
                oid = String.valueOf(AvidAndBvidUtil.bvidToAid(dto.getOid()));
            } else {
                oid = dto.getOid();
            }
            boolean result;
            try {
                result = esIndexService.indexExists(oid);
                if (result) {
                    // 删除索引
                    esIndexService.deleteIndex(oid);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return MsgUtil.fail("添加失败", e.getMessage());
        }
    }

    @ApiOperation("查询文档")
    @PostMapping("/getDocument")
    public MsgUtil<Object> getDocument(@RequestBody EsQueryDTO<BilibiliComment> dto) {
        try {
            if ( !"11".equals(dto.getType()) ) {
                String oid = String.valueOf(AvidAndBvidUtil.bvidToAid(dto.getIndexName()));
                dto.setIndexName(oid);
            }
            Map<String, Object> comments = esDocumentService.searchByQueryObject(dto);
            return MsgUtil.success("查询成功", comments.get("data"), (PageUtil) comments.get("page"));
        } catch (ElasticsearchStatusException e) {
            return MsgUtil.fail("该bv号暂时没有爬取", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return MsgUtil.fail("查询失败", e.getMessage());
        }
    }

//    @EnableAsync
//    @ApiOperation("强行重新爬取，现在已经废弃")
//    @PostMapping("/reAdd")
    public MsgUtil<Object> reAdd(@RequestBody MapCreateDTO dto) {
        try {
            Map<String, String> map = bilibiliCommentService.convertMap(dto);
            if ( !"11".equals(dto.getType()) ) {
                String oid = String.valueOf(AvidAndBvidUtil.bvidToAid(dto.getOid()));
                dto.setOid(oid);
                map.put("oid", oid);
            }
            boolean result = esIndexService.indexExists(dto.getOid());
            if (result) {
                // 判断进度有没有完成100%
                AsyncMsgUtil asyncMsg = asyncService.findAsyncMsgUtil(dto.getOid());
                if (!"100".equals(asyncMsg.getProgress())) {
                    return MsgUtil.fail("请先等待任务执行完毕");
                }
                // 删除索引
                esIndexService.deleteIndex(dto.getOid());
            }
            esIndexService.createIndex(dto.getOid());
            bilibiliCommentService.addComment(map);
            return MsgUtil.success("添加成功");
        } catch (Exception e) {
            e.printStackTrace();
            return MsgUtil.fail("添加失败", e.getMessage());
        }
    }

    @ApiOperation("删除缓存")
    @GetMapping("/deleteIndex")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "bvid", value = "BV号", required = true, dataType = "string", paramType = "query")
    })
    public MsgUtil<Object> deleteIndex(String bvid) {
        try {
            bvid = String.valueOf(AvidAndBvidUtil.bvidToAid(bvid));
            boolean result = esIndexService.indexExists(bvid);
            if (result) {
                // 删除索引
                esIndexService.deleteIndex(bvid);
                return MsgUtil.success("清理成功");
            }
            return MsgUtil.fail("没有该视频的缓存");
        } catch (Exception e) {
            e.printStackTrace();
            return MsgUtil.fail("清理失败");
        }

    }


}
