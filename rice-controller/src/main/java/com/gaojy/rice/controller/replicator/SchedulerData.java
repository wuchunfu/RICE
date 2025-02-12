package com.gaojy.rice.controller.replicator;

import io.netty.channel.Channel;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author gaojy
 * @ClassName SchedulerData.java
 * @Description
 * @createTime 2022/08/01 14:26:00
 */
public class SchedulerData implements Serializable {

    private String address;
    //private Channel channel;
    private Double CPUPercent;

    private Double memoryPercent;

    private Integer taskNum;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Double getCPUPercent() {
        return CPUPercent;
    }

    public void setCPUPercent(Double CPUPercent) {
        this.CPUPercent = CPUPercent;
    }

    public Double getMemoryPercent() {
        return memoryPercent;
    }

    public void setMemoryPercent(Double memoryPercent) {
        this.memoryPercent = memoryPercent;
    }

    public Integer getTaskNum() {
        return taskNum;
    }

    public void setTaskNum(Integer taskNum) {
        this.taskNum = taskNum;
    }

    public Map<String,String> getMap(){
        Map<String,String> map = new HashMap<>();
        map.put("taskNum",getTaskNum()+"");
        map.put("memoryPercent",getMemoryPercent()+"");
        map.put("CPUPercent",getCPUPercent()+"");
        map.put("address",getAddress()+"");
        return map;
    }

    public SchedulerData(String address, Double CPUPercent, Double memoryPercent, Integer taskNum) {
        this.address = address;
        this.CPUPercent = CPUPercent;
        this.memoryPercent = memoryPercent;
        this.taskNum = taskNum;
    }
}
