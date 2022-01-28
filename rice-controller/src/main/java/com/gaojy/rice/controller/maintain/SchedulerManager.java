package com.gaojy.rice.controller.maintain;

import com.gaojy.rice.common.protocol.body.processor.TaskDetailData;
import io.netty.channel.Channel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * @author gaojy
 * @ClassName SchedulerManager.java
 * @Description 调度器状态维护
 * @createTime 2022/01/18 22:48:00
 */
public class SchedulerManager {
    private Set<ChannelWrapper> schedulerNodes = new HashSet<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock r = lock.readLock();    //读锁
    private final Lock w = lock.writeLock();    //写锁

    private static volatile SchedulerManager instance = null;

    private SchedulerManager() {
    }

    public static SchedulerManager getManager() {
        if (instance == null) {
            synchronized (SchedulerManager.class) {
                if (instance == null) {
                    instance = new SchedulerManager();
                }
            }

        }
        return instance;
    }

    public List<String> getActiveSchedulerAddr() {
        r.lock();
        List<String> rets = null;
        try {
            rets = schedulerNodes.stream().filter(c -> c.isActive()).
                map(ChannelWrapper::getRemoteAddr).collect(Collectors.toList());
        } finally {
            r.unlock();
        }
        return rets;
    }

    public void registerScheduler(Channel channel) {
        w.lock();
        try {
            Set<ChannelWrapper> newSchedulerNodes = new HashSet<>(schedulerNodes);
            newSchedulerNodes.add(new ChannelWrapper(channel));
            schedulerNodes = newSchedulerNodes;
            // 如果是master
            // 任务分配
        } finally {
            w.unlock();
        }
    }

    public Boolean updateSchedulerStatus(Channel channel) {
        // 比如获取最新的心跳时间
        return Boolean.TRUE;
    }

    public void crashOrDown(Channel channel) {
        // 判断是否在内存中

        // 如果在 则移除

        // 如果是master 则任务分配

    }

    public boolean is_scheduler(String remoteAddr) {
        r.lock();
        boolean is_scheduler = false;
        try {
            is_scheduler = schedulerNodes.stream()
                .map(ChannelWrapper::getRemoteAddr).collect(Collectors.toList())
                .contains(remoteAddr);
        } finally {
            r.unlock();
        }
        return is_scheduler;
    }

    public Boolean notifyTaskRegister(String schedulerServer, String taskCode, String processorAddr) {
        ChannelWrapper cw = schedulerNodes.stream().filter(node
            -> node.getRemoteAddr().equals(schedulerServer)).findAny().orElse(null);
        return false;

    }

}
