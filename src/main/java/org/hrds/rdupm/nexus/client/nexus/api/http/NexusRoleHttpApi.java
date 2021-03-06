package org.hrds.rdupm.nexus.client.nexus.api.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.choerodon.core.exception.CommonException;
import org.apache.commons.lang3.StringUtils;
import org.hrds.rdupm.nexus.client.nexus.NexusRequest;
import org.hrds.rdupm.nexus.client.nexus.api.NexusRoleApi;
import org.hrds.rdupm.nexus.client.nexus.constant.NexusApiConstants;
import org.hrds.rdupm.nexus.client.nexus.constant.NexusUrlConstants;
import org.hrds.rdupm.nexus.client.nexus.exception.NexusResponseException;
import org.hrds.rdupm.nexus.client.nexus.model.NexusServerRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * nexus 角色API
 * @author weisen.yang@hand-china.com 2020/3/18
 */
@Component
public class NexusRoleHttpApi implements NexusRoleApi{

	private static final Logger LOGGER = LoggerFactory.getLogger(NexusRoleHttpApi.class);

	@Autowired
	private NexusRequest nexusRequest;

	@Override
	public List<NexusServerRole> getRoles() {
		ResponseEntity<String> responseEntity = nexusRequest.exchange(NexusUrlConstants.Role.GET_ROLE_LIST, HttpMethod.GET, null, null);
		String response = responseEntity.getBody();
		return JSONObject.parseArray(response, NexusServerRole.class);
	}

	@Override
	public NexusServerRole getRoleById(String roleId) {
		if (StringUtils.isBlank(roleId)) {
			return null;
		}
		String url = NexusUrlConstants.Role.GET_ROLE_BY_ID + roleId;
		ResponseEntity<String> responseEntity = null;
		try {
			responseEntity = nexusRequest.exchange(url, HttpMethod.GET, null, null);
		} catch (NexusResponseException e) {
			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				return null;
			} else {
				throw e;
			}
		}
		String response = responseEntity.getBody();
		return JSON.parseObject(response, NexusServerRole.class);
	}

	@Override
	public void deleteRole(String roleId) {
		String url = NexusUrlConstants.Role.DELETE_ROLE + roleId;
		try {
			ResponseEntity<String> responseEntity = nexusRequest.exchange(url, HttpMethod.DELETE, null, null);
		} catch (NexusResponseException e) {
			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				LOGGER.warn("nexus role has been deleted");
			} else {
				throw e;
			}
		}
	}

	@Override
	public void createRole(NexusServerRole nexusRole) {
		// 唯一性校验
		/*NexusServerRole existRole = this.getRoleById(nexusRole.getId());
		if (existRole != null) {
			throw new CommonException(NexusApiConstants.ErrorMessage.ROLE_EXIST);
		}*/
		ResponseEntity<String> responseEntity = nexusRequest.exchange(NexusUrlConstants.Role.CREATE_ROLE, HttpMethod.POST, null, nexusRole);
	}

	@Override
	public void updateRole(NexusServerRole nexusRole) {
		String url = NexusUrlConstants.Role.UPDATE_ROLE + nexusRole.getId();
		ResponseEntity<String> responseEntity = nexusRequest.exchange(url, HttpMethod.PUT, null, nexusRole);
	}
}
