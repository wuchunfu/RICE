package com.gaojy.rice.processor.api;

/**
 * @author gaojy
 * @ClassName RiceMapReduceProccessor.java
 * @Description MAP Reduce任务执行接口
 * @createTime 2022/01/02 14:07:00
 */
public interface RiceMapReduceProccessor extends RiceBasicProcessor, RiceMapProcessor {

    public ProcessResult reduce(TaskContext context);

}
