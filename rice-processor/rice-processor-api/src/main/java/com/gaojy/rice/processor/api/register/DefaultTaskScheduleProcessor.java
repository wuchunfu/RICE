package com.gaojy.rice.processor.api.register;

import com.gaojy.rice.common.constants.LoggerName;
import com.gaojy.rice.common.constants.RequestCode;
import com.gaojy.rice.common.constants.TaskInstanceStatus;
import com.gaojy.rice.common.exception.ProcessorException;
import com.gaojy.rice.common.protocol.body.scheduler.TaskInvokerResponseBody;
import com.gaojy.rice.common.protocol.header.CommandCustomHeader;
import com.gaojy.rice.common.protocol.header.scheduler.TaskInvokeRequestHeader;
import com.gaojy.rice.common.protocol.header.scheduler.TaskInvokerResponseHeader;
import com.gaojy.rice.processor.api.ProcessResult;
import com.gaojy.rice.processor.api.RiceProcessorManager;
import com.gaojy.rice.processor.api.TaskContext;
import com.gaojy.rice.processor.api.invoker.NoSuchMethodException;
import com.gaojy.rice.processor.api.invoker.TaskInvoker;
import com.gaojy.rice.processor.api.log.appender.ILogHandler;
import com.gaojy.rice.remote.common.RemoteHelper;
import com.gaojy.rice.remote.common.TransfUtil;
import com.gaojy.rice.remote.protocol.RiceRemoteContext;
import com.gaojy.rice.remote.transport.RiceRequestProcessor;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

/**
 * @author gaojy
 * @ClassName DefaultTaskScheduleProcessor.java
 * @Description 处理调度器的调度请求
 * @createTime 2022/01/07 16:18:00
 */
public class DefaultTaskScheduleProcessor implements RiceRequestProcessor {

    private static final Logger log = LoggerFactory.getLogger(LoggerName.CLIENT_LOGGER_NAME);

    private RiceProcessorManager manager;

    public DefaultTaskScheduleProcessor(RiceProcessorManager manager) {
        this.manager = manager;
    }

    @Override
    public RiceRemoteContext processRequest(ChannelHandlerContext ctx, RiceRemoteContext request) throws Exception {

        switch (request.getCode()) {
            case RequestCode.INVOKE_PROCESSOR:
                Throwable error = null;
                ProcessResult result = null;
                Long startTime = System.currentTimeMillis();
                RiceRemoteContext response = RiceRemoteContext.createResponseCommand(TaskInvokerResponseHeader.class);
                TaskInvokerResponseHeader responseHeader = (TaskInvokerResponseHeader) response.readCustomHeader();

                // 获取来自scheduler的数据
                TaskInvokeRequestHeader requestHeader = (TaskInvokeRequestHeader) request.decodeCommandCustomHeader(TaskInvokeRequestHeader.class);

                responseHeader.setTaskCode(requestHeader.getTaskCode());
                int retryTime = requestHeader.getMaxRetryTimes();
                responseHeader.setTaskInstanceStatus(TaskInstanceStatus.FINISHED.name());

                // 根据taskcode来获取对应的invoker
                TaskInvoker invoker = TaskInvoker.getInvoker(requestHeader.getTaskCode());
                if (invoker == null) {
                    error = new ProcessorException("没有找到对应的任务执行器");
                } else {
                    if (invoker.getTaskDetailData().isLogEnable()) {
                        log.info("任务：" + requestHeader.getTaskCode() + ",已开启实时日志,将传送日志至调度器:" +
                            RemoteHelper.parseChannelRemoteAddr(ctx.channel()));
                        ILogHandler.schedulersOfLog.put(requestHeader.getTaskInstanceId(), ctx.channel());
                    }

                    TaskContext taskContext = new TaskContext();
                    taskContext.setChannel(ctx.channel());
                    taskContext.setParameter(requestHeader.getInstanceParameter());
                    taskContext.setTaskCode(requestHeader.getTaskCode());
                    taskContext.setTaskInstanceId(requestHeader.getTaskInstanceId());

                    while (retryTime >= 0) {
                        try {
                            result = (ProcessResult) invoker.invokeMethod(invoker
                                    .getInvokerInstance(requestHeader.getTaskCode()), requestHeader.getMethodName(),
                                new Class[] {TaskContext.class}, new Object[] {taskContext});
                            break;
                        } catch (Exception e) {
                            log.error("Exception occurred while processing task:{},error:{}", requestHeader.getTaskCode(), e);
                            error = e;
                        }
                        retryTime--;
                    }
                }

                Long finishTime = System.currentTimeMillis();
                // 包装响应数据
                // SET header
                responseHeader.setFinishTime(finishTime);
                responseHeader.setRunningTime(finishTime - startTime);
                responseHeader.setRetryTimes(requestHeader.getMaxRetryTimes() - retryTime);
                // SET body
                TaskInvokerResponseBody responseBody = new TaskInvokerResponseBody();
                if (error != null) {
                    responseBody.getResultMap().put("error", error.getMessage());
                    responseHeader.setTaskInstanceStatus(TaskInstanceStatus.EXCEPTION.name());
                } else if (result != null) {
                    responseBody.setResultMap(result.getData());
                }
                response.setBody(responseBody.encode());
                if (invoker.getTaskDetailData().isLogEnable()) {
                    log.info("本次任务实例：" + requestHeader.getTaskInstanceId() + ",已完成调度，将停止发送日志数据");
                    ILogHandler.schedulersOfLog.remove(requestHeader.getTaskInstanceId());
                }
                return response;
            case RequestCode.SCHEDULER_HEART_BEAT:
                String address = RemoteHelper.parseChannelRemoteAddr(ctx.channel());
                log.info("Heartbeat probe received from scheduler:{}", address);
            default:
                return null;
        }

    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
