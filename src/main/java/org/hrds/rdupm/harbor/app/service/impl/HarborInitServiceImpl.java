package org.hrds.rdupm.harbor.app.service.impl;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.alibaba.fastjson.JSONObject;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import io.choerodon.asgard.saga.annotation.Saga;
import io.choerodon.asgard.saga.producer.StartSagaBuilder;
import io.choerodon.asgard.saga.producer.TransactionalProducer;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.iam.ResourceLevel;
import org.apache.commons.collections.CollectionUtils;
import org.hrds.rdupm.harbor.api.vo.FdProjectDto;
import org.hrds.rdupm.harbor.api.vo.HarborAuthVo;
import org.hrds.rdupm.harbor.api.vo.HarborCountVo;
import org.hrds.rdupm.harbor.app.service.C7nBaseService;
import org.hrds.rdupm.harbor.app.service.HarborInitService;
import org.hrds.rdupm.harbor.config.HarborInitConfiguration;
import org.hrds.rdupm.harbor.domain.entity.HarborAuth;
import org.hrds.rdupm.harbor.domain.entity.HarborProjectDTO;
import org.hrds.rdupm.harbor.domain.entity.HarborRepository;
import org.hrds.rdupm.harbor.domain.entity.User;
import org.hrds.rdupm.harbor.domain.repository.HarborAuthRepository;
import org.hrds.rdupm.harbor.domain.repository.HarborRepositoryRepository;
import org.hrds.rdupm.harbor.infra.annotation.OperateLog;
import org.hrds.rdupm.harbor.infra.constant.HarborConstants;
import org.hrds.rdupm.harbor.infra.feign.dto.UserDTO;
import org.hrds.rdupm.harbor.infra.util.HarborHttpClient;
import org.hzero.core.base.BaseConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * description
 *
 * @author chenxiuhong 2020/05/28 2:37 下午
 */
@Service
public class HarborInitServiceImpl implements HarborInitService {

	@Resource
	private HarborHttpClient harborHttpClient;
	@Autowired
	private C7nBaseService c7nBaseService;
	@Autowired
	private HarborRepositoryRepository harborRepositoryRepository;
	@Autowired
	private HarborInitConfiguration harborInitConfiguration;
	@Autowired
	private HarborAuthRepository repository;
	@Resource
	private TransactionalProducer transactionalProducer;

	private final String sagaCode = "rdupm-docker-auth-create-init";
	private final String sagaCodeUser = "rdupm-docker-auth-create-init.user";
	private final String sagaCodeAuth = "rdupm-docker-auth-create-init.auth";
	private final String sagaCodeDb = "rdupm-docker-auth-create-init.db";


	@Override
	public void init(){
		ResponseEntity<String> countResponse = harborHttpClient.exchange(HarborConstants.HarborApiEnum.COUNT,null,null,true);
		HarborCountVo harborCountVo = JSONObject.parseObject(countResponse.getBody(), HarborCountVo.class);

		ResponseEntity<String> projectResponse = harborHttpClient.exchange(HarborConstants.HarborApiEnum.LIST_PROJECT,null,null,true);
		Map<String,HarborProjectDTO> map = new HashMap<>(16);
		List<String> projectList= JSONObject.parseArray(projectResponse.getBody(),String.class);
		Gson gson = new Gson();
		for(String object : projectList){
			HarborProjectDTO projectResponseDto = gson.fromJson(object, HarborProjectDTO.class);
			map.put(projectResponseDto.getName(),projectResponseDto);
		}

		String selectSql = "SELECT\n" +
				"\tfp.`CODE` code,fp.`NAME` name,fp.id projectId,fp.ORGANIZATION_ID organizationId,fp.CREATED_BY createdBy,ht.tenant_num tenantNum,ht.tenant_name tenantName,\n" +
				"\tconcat( ht.tenant_num, CONCAT( '-', fp.`CODE` ) ) tenantProjectCode\n" +
				"FROM\n" +
				"\tfd_project fp\n" +
				"\tLEFT JOIN hpfm_tenant ht ON fp.ORGANIZATION_ID = ht.TENANT_ID";
		List<FdProjectDto> fdProjectDtoList =  getJdbcTemplate().query(selectSql,new BeanPropertyRowMapper<>(FdProjectDto.class));

		List<HarborRepository> harborRepositoryList = new ArrayList<>();
		Map<Long,Long> userMap = new HashMap<>(16);
		Set<Long> userIdSet = new HashSet<>(16);
		if(CollectionUtils.isNotEmpty(fdProjectDtoList)){
			fdProjectDtoList.stream().forEach(dto->{
				HarborProjectDTO harborProjectDTO= map.get(dto.getTenantProjectCode());
				if(harborProjectDTO != null){
					HarborRepository harborRepository = new HarborRepository(dto.getProjectId(),dto.getTenantProjectCode(),dto.getName(),harborProjectDTO.getMetadata().getPublicFlag(),Long.parseLong(harborProjectDTO.getHarborId().toString()),dto.getOrganizationId());
					harborRepositoryList.add(harborRepository);
					if(dto.getCreatedBy() == 0){
						UserDTO userDTO = c7nBaseService.getProjectOwnerById(dto.getProjectId());
						userIdSet.add(userDTO.getId());
						userMap.put(dto.getProjectId(),userDTO.getId());
					}else {
						userIdSet.add(dto.getCreatedBy());
						userMap.put(dto.getProjectId(),dto.getCreatedBy());
					}
				}
			});
			//批量保存项目
			//创建用户、分配权限
			harborRepositoryList.forEach(dto->{
				if(CollectionUtils.isEmpty(harborRepositoryRepository.select(HarborRepository.FIELD_PROJECT_ID,dto.getProjectId()))){
					harborRepositoryRepository.insertSelective(dto);
				}

				List<HarborAuth> authList = new ArrayList<>();
				HarborAuth harborAuth = new HarborAuth();
				harborAuth.setUserId(userMap.get(dto.getProjectId()));
				harborAuth.setHarborRoleValue(HarborConstants.HarborRoleEnum.PROJECT_ADMIN.getRoleValue());
				try {
					harborAuth.setEndDate(new SimpleDateFormat(BaseConstants.Pattern.DATE).parse("2099-12-31"));
				} catch (ParseException e) {
					e.printStackTrace();
				}
				authList.add(harborAuth);
				save(dto.getProjectId(),authList);
			});
		}
	}


	private JdbcTemplate getJdbcTemplate(){
		MysqlDataSource mysqlDataSource = new MysqlDataSource();
		mysqlDataSource.setURL(harborInitConfiguration.getUrl());
		mysqlDataSource.setUser(harborInitConfiguration.getUsername());
		mysqlDataSource.setPassword(harborInitConfiguration.getPassword());
		JdbcTemplate jdbcTemplate = new JdbcTemplate(mysqlDataSource);
		return jdbcTemplate;
	}

	@OperateLog(operateType = HarborConstants.ASSIGN_AUTH,content = "%s 分配 %s 权限角色为 【%s】,过期日期为【%s】")
	@Saga(code = sagaCode,description = "分配权限",inputSchemaClass = List.class)
	public void save(Long projectId,List<HarborAuth> dtoList) {
		if(CollectionUtils.isEmpty(dtoList)){
			throw new CommonException("error.harbor.auth.param.empty");
		}
		HarborRepository harborRepository = harborRepositoryRepository.select(HarborRepository.FIELD_PROJECT_ID,projectId).stream().findFirst().orElse(null);
		if(harborRepository == null){
			throw new CommonException("error.harbor.project.not.exist");
		}

		//校验是否已分配权限
		List<HarborAuth> existList = repository.select(HarborAuth.FIELD_PROJECT_ID,projectId);
		Map<Long,HarborAuth> harborAuthMap = CollectionUtils.isEmpty(existList) ? new HashMap<>(1) : existList.stream().collect(Collectors.toMap(HarborAuth::getUserId,dto->dto));

		Set<Long> userIdSet = dtoList.stream().map(dto->dto.getUserId()).collect(Collectors.toSet());
		Map<Long,UserDTO> userDtoMap = c7nBaseService.listUsersByIds(userIdSet);
		dtoList.forEach(dto->{
			UserDTO userDTO = userDtoMap.get(dto.getUserId());
			dto.setLoginName(userDTO == null ? null : userDTO.getLoginName());
			dto.setRealName(userDTO == null ? null : userDTO.getRealName());

			if(harborAuthMap.get(dto.getUserId()) != null){
				throw new CommonException("error.harbor.auth.already.exist",dto.getRealName());
			}

			dto.setProjectId(projectId);
			dto.setOrganizationId(harborRepository.getOrganizationId());
			dto.setHarborId(harborRepository.getHarborId());
			dto.setHarborRoleValue(dto.getHarborRoleValue());
			dto.setHarborAuthId(-1L);
		});

		transactionalProducer.apply(StartSagaBuilder.newBuilder()
						.withSagaCode(sagaCode)
						.withLevel(ResourceLevel.PROJECT)
						.withRefType("dockerRepo")
						.withSourceId(projectId),
				startSagaBuilder -> {

					//保存到数据库
					Long harborId = dtoList.get(0).getHarborId();
					ResponseEntity<String> responseEntity = harborHttpClient.exchange(HarborConstants.HarborApiEnum.LIST_AUTH,null,null,true,harborId);
					List<HarborAuthVo> harborAuthVoList = new Gson().fromJson(responseEntity.getBody(),new TypeToken<List<HarborAuthVo>>(){}.getType());
					Map<String,HarborAuthVo> harborAuthVoMap = CollectionUtils.isEmpty(harborAuthVoList) ? new HashMap<>(1) : harborAuthVoList.stream().collect(Collectors.toMap(HarborAuthVo::getEntityName,dto->dto));
					dtoList.stream().forEach(dto->{
						if(harborAuthVoMap.get(dto.getLoginName()) != null){
							throw new CommonException("error.harbor.auth.find.harborAuthId");
						}
					});
					repository.batchInsert(dtoList);

					startSagaBuilder.withPayloadAndSerialize(dtoList).withSourceId(projectId);
				});
	}

}