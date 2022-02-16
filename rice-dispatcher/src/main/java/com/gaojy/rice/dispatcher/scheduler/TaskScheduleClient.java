package com.gaojy.rice.dispatcher.scheduler;

import com.alipay.remoting.LifeCycleException;
import com.gaojy.rice.common.RiceThreadFactory;
import com.gaojy.rice.common.balance.Balance;
import com.gaojy.rice.common.balance.RandomBalance;
import com.gaojy.rice.common.constants.ExecuteType;
import com.gaojy.rice.common.constants.LoggerName;
import com.gaojy.rice.common.constants.ScheduleType;
import com.gaojy.rice.common.constants.TaskStatus;
import com.gaojy.rice.common.constants.TaskType;
import com.gaojy.rice.common.entity.ProcessorServerInfo;
import com.gaojy.rice.common.entity.RiceTaskInfo;
import com.gaojy.rice.common.timewheel.HashedWheelTimer;
import com.gaojy.rice.common.timewheel.Timeout;
import com.gaojy.rice.common.timewheel.TimerTask;
import com.gaojy.rice.dispatcher.common.DispatcherAPIWrapper;
import com.gaojy.rice.dispatcher.longpolling.PullRequest;
import com.gaojy.rice.dispatcher.longpolling.PullTaskService;
import com.gaojy.rice.dispatcher.scheduler.tasktype.RiceExecuter;
import com.gaojy.rice.dispatcher.scheduler.tasktype.TaskExecuterFactory;
import com.gaojy.rice.remote.InvokeCallback;
import com.gaojy.rice.remote.protocol.RiceRemoteContext;
import com.gaojy.rice.remote.transport.ResponseFuture;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gaojy
 * @ClassName TaskScheduleClient.java
 * @Description 任务调度单位
 * @createTime 2022/02/11 11:12:00
 */
public class TaskScheduleClient implements TimerTask, LifeCycle {
    private static final Logger log = LoggerFactory.getLogger(LoggerName.DISPATCHER_LOGGER_NAME);
    // 所有的调度使用一个时间轮
    private final HashedWheelTimer scheduleTimer;
    private String taskCode;
    private String taskName;
    private TaskType taskType;
    private String parameters;
    private ScheduleType scheduleType = ScheduleType.CRON;
    private String timeExpression;
    private ExecuteType executeType;
    private int executeThreads = 1;
    private int taskRetryCount = 0;
    private int instanceRetryCount = 0;
    private CronExpression cexpStart;
    private volatile Set<String> processes = new CopyOnWriteArraySet();
    private AtomicReference<TaskStatus> taskStatus = new AtomicReference<>(TaskStatus.ONLINE);
    private DispatcherAPIWrapper outApiWrapper;
    private ExecutorService threadPool;
    // 任务启动时间
    private Long bootTime = System.currentTimeMillis();
    private PullTaskService pullTaskService;
    private TaskScheduleManager taskScheduleManager;
    private Balance balance = new RandomBalance();
    private final RiceExecuter riceExecuter;

    public TaskScheduleClient(RiceTaskInfo taskInfo,
        TaskScheduleManager taskScheduleManager) throws ParseException {
        buildClient(taskInfo);
        this.scheduleTimer = taskScheduleManager.getScheduleTimer();
        this.outApiWrapper = taskScheduleManager.getOutApiWrapper();
        this.pullTaskService = taskScheduleManager.getPullTaskService();
        threadPool = Executors.newFixedThreadPool(executeThreads, new RiceThreadFactory(taskCode + "_thread"));
        if (ScheduleType.CRON.equals(scheduleType)) {
            cexpStart = new CronExpression(timeExpression);
        }
        riceExecuter = TaskExecuterFactory.getExecuter(taskType, this);

    }

    private void buildClient(RiceTaskInfo taskInfo) {
        try {
            BeanUtils.copyProperties(this, taskInfo);
            this.executeType = ExecuteType.getType(taskInfo.getExecuteType());
            this.taskType = TaskType.getType(taskInfo.getTaskType());
            this.scheduleType = scheduleType.getType(taskInfo.getScheduleType());
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void run(Timeout timeout) throws Exception {
        if (taskStatus.get().equals(TaskStatus.OFFLINE)) {
            taskScheduleManager.taskStatusChange(taskCode, TaskStatus.OFFLINE);
            return;
        }

        // 不同的任务类型  具体的执行步骤又是不一样的
        if (taskStatus.get().equals(TaskStatus.ONLINE)) {
            riceExecuter.execute();
        } else if (taskStatus.get().equals(TaskStatus.PAUSE)) { //跳过本次执行
            log.info("taskCode={},status is pause", taskCode);
        }
        // 计算下次执行
        long delay = 0L;
        if (ScheduleType.CRON.equals(scheduleType)) {
            Date current = new Date();
            Date firstStartTime = cexpStart.getNextValidTimeAfter(current);
            delay = firstStartTime.getTime() - current.getTime();
        }
        if (ScheduleType.FIX_RATE.equals(scheduleType) || ScheduleType.FIX_DELAY.equals(scheduleType)) {
            delay = Long.parseLong(timeExpression);
        }

        // todo 下次的理论执行时间写数据库

        scheduleTimer.newTimeout(this, delay, TimeUnit.MILLISECONDS);
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TaskScheduleClient))
            return false;
        TaskScheduleClient client = (TaskScheduleClient) o;
        return taskCode.equals(client.taskCode);
    }

    @Override public int hashCode() {
        return Objects.hash(taskCode);
    }

    public Set<String> getProcesses() {
        return processes;
    }

    @Override
    public void startup() throws LifeCycleException {
        this.pullTaskService.executePullRequestImmediately(new PullRequest(this.bootTime, taskCode));
        Long delay = 0L;
        if (ScheduleType.FIX_DELAY.equals(scheduleType)) {
            // 固定延迟  则延迟调度
            delay = Long.parseLong(timeExpression);
        }
        this.scheduleTimer.newTimeout(this, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() throws LifeCycleException {
        taskStatus.set(TaskStatus.OFFLINE);
        threadPool.shutdown();

    }

    @Override
    public boolean isStarted() {
        return taskStatus.get() != TaskStatus.OFFLINE;
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    public List<String> selectProcessores(ExecuteType type) {
        List<String> selectRet = new ArrayList<>();
        type = executeType == null ? this.executeType : type;
        if (processes != null && processes.size() > 0) {
            List<String> all = processes.stream().collect(Collectors.toList());
            if (ExecuteType.STANDALONE.equals(executeType)) { // 单机执行
                // 负载均衡  找到一个处理器
                selectRet.add(balance.select(all));
            }
        }
        return selectRet;
    }

    public void invoke(String processorAddr, RiceRemoteContext remoteContext) {
        InvokeCallback callback = new InvokeCallback() {

            @Override
            public void operationComplete(ResponseFuture responseFuture) {
                // 更新数据库
                //
            }
        };
        outApiWrapper.invokeTask(processorAddr, remoteContext, callback);
    }

    public void initProcessores(List<ProcessorServerInfo> list) {
        if (list != null && list.size() > 0) {
            list.forEach(processor -> {
                processes.add(processor.getAddress() + ":" + processor.getPort());
            });
        }
    }

    public AtomicReference<TaskStatus> getTaskStatus() {
        return taskStatus;
    }

    public void setProcesses(Set<String> processes) {
        this.processes = processes;
    }
}
