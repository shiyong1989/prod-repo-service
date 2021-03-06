package org.hrds.rdupm.nexus.api.controller.v1;

import org.hzero.core.util.Results;
import org.hzero.core.base.BaseController;
import org.hrds.rdupm.nexus.domain.entity.NexusRole;
import org.hrds.rdupm.nexus.domain.repository.NexusRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.hzero.mybatis.helper.SecurityTokenHelper;

import io.choerodon.core.domain.Page;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
import springfox.documentation.annotations.ApiIgnore;

/**
 * 制品库_nexus仓库角色信息表 管理 API
 *
 * @author weisen.yang@hand-china.com 2020-03-27 11:42:59
 */
@RestController("nexusRoleController.v1")
@RequestMapping("/v1/{organizationId}/nexus-roles")
public class NexusRoleController extends BaseController {

    @Autowired
    private NexusRoleRepository nexusRoleRepository;


}
