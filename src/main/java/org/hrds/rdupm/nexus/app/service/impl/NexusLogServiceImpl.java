package org.hrds.rdupm.nexus.app.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hrds.rdupm.harbor.app.service.C7nBaseService;
import org.hrds.rdupm.harbor.infra.feign.dto.ProjectDTO;
import org.hrds.rdupm.nexus.app.service.NexusLogService;
import org.hrds.rdupm.nexus.domain.entity.NexusLog;
import org.hrds.rdupm.nexus.domain.repository.NexusLogRepository;
import org.hrds.rdupm.nexus.infra.mapper.NexusLogMapper;
import org.hrds.rdupm.nexus.infra.util.PageConvertUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
/**
 * 制品库_nexus日志表应用服务默认实现
 *
 * @author weisen.yang@hand-china.com 2020-05-26 22:55:13
 */
@Service
public class NexusLogServiceImpl implements NexusLogService {

    @Autowired
    private C7nBaseService c7nBaseService;
    @Autowired
    private NexusLogRepository nexusLogRepository;
    @Autowired
    private NexusLogMapper nexusLogMapper;

    @Override
    public Page<NexusLog> listLog(Long organizationId, String repoType, Long projectId, String neRepositoryName, String realName, String operateType, Date startDate, Date endDate, Long repositoryId, PageRequest pageRequest) {
        Page<NexusLog> page = PageHelper.doPageAndSort(pageRequest, ()-> nexusLogMapper.listLog(organizationId, repoType, projectId, neRepositoryName, realName, operateType, startDate, endDate, repositoryId));
        //List<NexusLog> nexusLogList = nexusLogMapper.listLog(organizationId, repoType, projectId, neRepositoryName, realName, operateType, startDate, endDate, repositoryId);

        Set<Long> projectIdSet = page.getContent().stream().map(NexusLog::getProjectId).collect(Collectors.toSet());
        Map<Long, ProjectDTO> projectDataMap = c7nBaseService.queryProjectByIds(projectIdSet);
        for (NexusLog log : page.getContent()) {
            ProjectDTO projectDTO = projectDataMap.get(log.getProjectId());
            if (null != projectDTO) {
                log.setProjectCode(projectDTO.getCode());
                log.setProjectName(projectDTO.getName());
                log.setProjectImageUrl(projectDTO.getImageUrl());
            }
        }
        return page;
    }
}
