package org.hrds.rdupm.nexus.api.controller.v1;

import io.choerodon.core.domain.Page;
import io.choerodon.swagger.annotation.Permission;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.hrds.rdupm.nexus.api.dto.*;
import org.hrds.rdupm.nexus.app.service.NexusRepositoryService;
import org.hrds.rdupm.nexus.domain.repository.NexusRepositoryRepository;
import org.hrds.rdupm.nexus.infra.constant.NexusConstants;
import org.hzero.core.base.BaseController;
import org.hzero.core.util.Results;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

/**
 * 组织层 制品库_nexus仓库信息表 管理 API
 *
 * @author weisen.yang@hand-china.com 2020-03-27 11:43:00
 */
@RestController("nexusRepositoryOrgController.v1")
@RequestMapping("/v1/nexus-repositorys/organizations")
public class NexusRepositoryOrgController extends BaseController {

    @Autowired
    private NexusRepositoryRepository nexusRepository;
    @Autowired
    private NexusRepositoryService nexusRepositoryService;

    @ApiOperation(value = "组织层-maven仓库列表")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/{organizationId}/maven/repo")
    public ResponseEntity<Page<NexusRepositoryDTO>> listOtherMavenRepo(@ApiParam(value = "组织ID", required = true) @PathVariable(name = "organizationId") Long organizationId,
                                                                           NexusRepositoryQueryDTO queryDTO,
                                                                           @ApiIgnore PageRequest pageRequest) {
        queryDTO.setOrganizationId(organizationId);
        queryDTO.setRepoType(NexusConstants.RepoType.MAVEN);
        return Results.success(nexusRepositoryService.listRepo(pageRequest, queryDTO, NexusConstants.RepoQueryData.REPO_ORG));
    }


    @ApiOperation(value = "组织层-npm仓库列表")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/{organizationId}/npm/repo")
    public ResponseEntity<Page<NexusRepositoryDTO>> listNpmRepo(@ApiParam(value = "组织ID", required = true) @PathVariable(name = "organizationId") Long organizationId,
                                                                NexusRepositoryQueryDTO queryDTO,
                                                                @ApiIgnore PageRequest pageRequest) {
        queryDTO.setOrganizationId(organizationId);
        queryDTO.setRepoType(NexusConstants.RepoType.NPM);
        return Results.success(nexusRepositoryService.listNpmRepo(pageRequest, queryDTO, NexusConstants.RepoQueryData.REPO_ORG));
    }
}
