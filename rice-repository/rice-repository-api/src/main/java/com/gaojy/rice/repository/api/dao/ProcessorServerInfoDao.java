package com.gaojy.rice.repository.api.dao;

import com.gaojy.rice.common.exception.RepositoryException;
import com.gaojy.rice.repository.api.entity.ProcessorServerInfo;
import java.sql.SQLException;
import java.util.List;

/**
 * @author gaojy
 * @ClassName ProcessorServerInfo.java
 * @Description TODO
 * @createTime 2022/01/27 21:24:00
 */
public interface ProcessorServerInfoDao {
    public List<ProcessorServerInfo> getInfosByServer(String address, int port) throws RepositoryException;

    public int batchCreateOrUpdateInfo(List<ProcessorServerInfo> processorServerInfoList) throws RepositoryException;

}
