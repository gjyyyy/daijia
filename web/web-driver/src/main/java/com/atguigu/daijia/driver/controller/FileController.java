package com.atguigu.daijia.driver.controller;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.FileService;
import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "上传管理接口")
@RestController
@RequestMapping("file")
public class FileController {

//    @Autowired
//    private CosService cosService;
    @Autowired
    private FileService fileService;

//    //文件上传接口
//    @Operation(summary = "上传")
//    //@CheckLogin
//    @PostMapping("/upload")
//    public Result<String> upload(@RequestPart("file") MultipartFile file,
//                                      @RequestParam(name = "path",defaultValue = "auth") String path){
//        CosUploadVo cosUploadVo = cosService.upload(file,path);
//        return Result.ok(cosUploadVo.getShowUrl());
//    }

    //文件上传接口
    @Operation(summary = "上传")
    @PostMapping("/upload")
    public Result<String> upload(@RequestPart("file") MultipartFile file){
        String url = fileService.upload(file);
        return Result.ok(url);
    }
}
