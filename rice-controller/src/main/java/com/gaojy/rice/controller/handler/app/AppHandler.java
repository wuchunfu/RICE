package com.gaojy.rice.controller.handler.app;

import com.gaojy.rice.common.entity.RiceAppInfo;
import com.gaojy.rice.common.utils.StringUtil;
import com.gaojy.rice.controller.handler.AbstractHttpHandler;
import com.gaojy.rice.controller.handler.PageSpec;
import com.gaojy.rice.http.api.HttpRequest;
import com.gaojy.rice.http.api.HttpResponse;
import com.gaojy.rice.http.api.RequestMapping;
import java.util.Date;

/**
 * @author gaojy
 * @ClassName AppHandler.java
 * @Description
 * @createTime 2022/12/04 23:28:00
 */
public class AppHandler extends AbstractHttpHandler {
    public AppHandler(String rootPath) {
        super(rootPath);
    }

    @RequestMapping(value = "/create")
    public HttpResponse create(HttpRequest request) throws Exception {
        String appName = (String) request.getParamMap().get("appName");
        String appDesc = (String) request.getParamMap().get("appDesc");
        if (StringUtil.isEmpty(appName)) {
            return new HttpResponse(400, "param error,missing appName");
        }
        RiceAppInfo appInfo = new RiceAppInfo();
        appInfo.setAppName(appName);
        appInfo.setAppDesc(appDesc);
        appInfo.setCreateTime(new Date());
        appInfo.setStatus(1);
        repository.getRiceAppInfoDao().createApp(appInfo);
        return new HttpResponse();
    }

    @RequestMapping(value = "/fetch")
    public HttpResponse fetch(HttpRequest request) throws Exception {

        String appName = (String) request.getParamMap().get("appName");
        Integer pageIndex = (Integer) request.getParamMap().get("pageIndex");
        Integer limit = (Integer) request.getParamMap().get("pageLimit");
        return new HttpResponse()
            .addResponse(
                "data",
                repository.getRiceAppInfoDao().queryApps(appName, pageIndex, limit))
            .addResponse("page",new PageSpec(pageIndex,limit,
                repository.getRiceAppInfoDao().queryAppsCount(appName)));
    }

    @RequestMapping(value = "/processor/info")
    public HttpResponse processorInfo(HttpRequest request) throws Exception {

        return null;
    }
}
