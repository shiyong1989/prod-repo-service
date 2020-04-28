package org.hrds.rdupm.harbor.app.service.impl;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageInfo;
import com.google.gson.Gson;
import io.choerodon.asgard.saga.annotation.Saga;
import io.choerodon.asgard.saga.annotation.SagaTask;
import io.choerodon.asgard.saga.producer.StartSagaBuilder;
import io.choerodon.asgard.saga.producer.TransactionalProducer;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hrds.rdupm.harbor.api.vo.HarborProjectVo;
import org.hrds.rdupm.harbor.app.service.HarborProjectService;
import org.hrds.rdupm.harbor.domain.entity.HarborProjectDTO;
import org.hrds.rdupm.harbor.domain.entity.HarborRepository;
import org.hrds.rdupm.harbor.domain.repository.HarborRepositoryRepository;
import org.hrds.rdupm.harbor.infra.constant.HarborConstants;
import org.hrds.rdupm.harbor.infra.dto.User;
import org.hrds.rdupm.harbor.infra.feign.BaseFeignClient;
import org.hrds.rdupm.harbor.infra.feign.dto.ProjectDTO;
import org.hrds.rdupm.harbor.infra.feign.dto.UserDTO;
import org.hrds.rdupm.harbor.infra.util.HarborHttpClient;
import org.hrds.rdupm.harbor.infra.util.HarborUtil;
import org.hrds.rdupm.nexus.infra.util.PageConvertUtils;
import org.hzero.core.util.AssertUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * description
 *
 * @author chenxiuhong 2020/04/21 10:54 上午
 */
@Service
public class HarborProjectServiceImpl implements HarborProjectService {

	@Autowired
	private HarborHttpClient harborHttpClient;

	@Resource
	private BaseFeignClient baseFeignClient;

	@Autowired
	private HarborRepositoryRepository harborRepositoryRepository;

	@Resource
	private TransactionalProducer transactionalProducer;

	//TODO
	private String userName = "15367";

	@Override
	@Saga(code = HarborConstants.HarborSagaCode.CREATE_PROJECT,description = "创建Docker镜像仓库",inputSchemaClass = HarborProjectVo.class)
	public void createSaga(Long projectId, HarborProjectVo harborProjectVo) {
		/*
		* 1.判断Harbor中是否存在当前用户
		* 2.获取当前用户登录名，调用猪齿鱼接口获取用户基本信息，新增用户到harbor
		* 3.根据projectId获取猪齿鱼项目信息，得到项目编码、组织ID
		* 4.创建harbor项目，存储容量、安全级别、其他配置等
		* 5.数据库保存harbor项目，并关联猪齿鱼ID
		* */
		//获取猪齿鱼项目信息
		ResponseEntity<ProjectDTO> projectDTOResponseEntity = baseFeignClient.query(projectId);
		ProjectDTO projectDTO = projectDTOResponseEntity.getBody();
		String code = projectDTO.getCode();
		harborProjectVo.setCode(code);
		harborProjectVo.setProjectDTO(projectDTO);

		//校验项目是否已经存在、校验数据正确性
		checkParam(harborProjectVo);
		checkProject(harborProjectVo,projectId);

		transactionalProducer.apply(StartSagaBuilder.newBuilder()
									.withSagaCode(HarborConstants.HarborSagaCode.CREATE_PROJECT)
									.withLevel(ResourceLevel.PROJECT)
									.withRefType("dockerRepo")
									.withSourceId(projectId),
								startSagaBuilder -> { startSagaBuilder.withPayloadAndSerialize(harborProjectVo).withSourceId(projectId); }
		);
	}

	@Override
	public HarborProjectVo detail(Long harborId) {
		Gson gson = new Gson();
		ResponseEntity<String> detailResponseEntity = harborHttpClient.exchange(HarborConstants.HarborApiEnum.DETAIL_PROJECT,null,null,false,harborId);
		HarborProjectDTO harborProjectDTO = gson.fromJson(detailResponseEntity.getBody(), HarborProjectDTO.class);
		HarborProjectVo harborProjectVo = new HarborProjectVo(harborProjectDTO);

		//获取存储容量
		ResponseEntity<String> summaryResponseEntity = harborHttpClient.exchange(HarborConstants.HarborApiEnum.GET_PROJECT_SUMMARY,null,null,false,harborId);
		Map<String,Object> summaryMap = gson.fromJson(summaryResponseEntity.getBody(),Map.class);
		Map<String,Object> quotaMap = (Map<String, Object>) summaryMap.get("quota");
		Map<String,Object> hardMap = (Map<String, Object>) quotaMap.get("hard");
		Map<String,Object> usedMap = (Map<String, Object>) quotaMap.get("used");
		Double hardCount = (Double) hardMap.get("count");
		Double hardStorage = (Double) hardMap.get("storage");
		Double usedCount = (Double) usedMap.get("count");
		Double usedStorage = (Double) usedMap.get("storage");

		harborProjectVo.setCountLimit(Double.valueOf(hardCount).intValue());
		harborProjectVo.setUsedCount(Double.valueOf(usedCount).intValue());
		harborProjectVo.setStorageLimit(Double.valueOf(hardStorage).intValue());
		harborProjectVo.setUsedStorage(Double.valueOf(usedStorage).intValue());

		Map<String,Object> storageLimitMap = HarborUtil.getStorageNumUnit(Double.valueOf(hardStorage).intValue());
		harborProjectVo.setStorageNum((Integer) storageLimitMap.get("storageNum"));
		harborProjectVo.setStorageUnit((String) storageLimitMap.get("storageUnit"));
		Map<String,Object> usedStorageMap = HarborUtil.getStorageNumUnit(Double.valueOf(usedStorage).intValue());
		harborProjectVo.setUsedStorageNum((Integer) usedStorageMap.get("storageNum"));
		harborProjectVo.setUsedStorageUnit((String) usedStorageMap.get("storageUnit"));

		//获取镜像仓库名称
		HarborRepository harborRepository = harborRepositoryRepository.select(HarborRepository.FIELD_HARBOR_ID,harborId).stream().findFirst().orElse(null);
		harborProjectVo.setName(harborRepository == null ? null : harborRepository.getName());

		return harborProjectVo;
	}

	@Override
	@Saga(code = HarborConstants.HarborSagaCode.UPDATE_PROJECT,description = "更新Docker镜像仓库",inputSchemaClass = HarborProjectVo.class)
	public void updateSaga(Long projectId, HarborProjectVo harborProjectVo) {
		HarborRepository harborRepository = harborRepositoryRepository.select(HarborRepository.FIELD_PROJECT_ID,projectId).stream().findFirst().orElse(null);
		if(harborRepository == null){
			throw new CommonException("error.harbor.project.not.exist");
		}
		harborProjectVo.setHarborId(harborRepository.getHarborId().intValue());

		/**
		 * 1.校验数据必输性
		 * 2.更新harbor项目元数据
		 * 3.更新项目资源配额
		 * 4.更新项目白名单
		 * 5.更新数据库项目
		 * */
		checkParam(harborProjectVo);

		transactionalProducer.apply(StartSagaBuilder.newBuilder()
						.withSagaCode(HarborConstants.HarborSagaCode.UPDATE_PROJECT)
						.withLevel(ResourceLevel.PROJECT)
						.withRefType("dockerRepo")
						.withSourceId(projectId),
				startSagaBuilder -> {
					if(!harborRepository.getPublicFlag().equals(harborProjectVo.getPublicFlag())){
						harborRepository.setPublicFlag(harborProjectVo.getPublicFlag());
						harborRepositoryRepository.updateByPrimaryKeySelective(harborRepository);
					}
					startSagaBuilder.withPayloadAndSerialize(harborProjectVo).withSourceId(projectId);
				}
		);
	}

	@Override
	public List<HarborRepository> listByProject(Long projectId, HarborRepository dto) {
		return 	harborRepositoryRepository.select(HarborRepository.FIELD_PROJECT_ID,projectId);
	}

	@Override
	public PageInfo<HarborRepository> listByOrg(Long organizationId,PageRequest pageRequest) {
		Page<HarborRepository> page = PageHelper.doPageAndSort(pageRequest, () -> harborRepositoryRepository.select(HarborRepository.FIELD_ORGANIZATION_ID,organizationId));
		processHarborRepositoryList(page.getContent());
		return PageConvertUtils.convert(page);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void delete(Long projectId) {
		HarborRepository harborRepository = harborRepositoryRepository.select(HarborRepository.FIELD_PROJECT_ID,projectId).stream().findFirst().orElse(null);
		if(harborRepository == null){
			throw new CommonException("error.harbor.project.not.exist");
		}
		harborRepositoryRepository.deleteByPrimaryKey(harborRepository.getId());
		harborHttpClient.exchange(HarborConstants.HarborApiEnum.DELETE_PROJECT,null,null,false,harborRepository.getHarborId());
	}

	/***
	 * 处理镜像仓库列表：查询镜像数、获得创建人登录名、真实名称、创建人头像
	 * @param harborRepositoryList
	 */
	private void processHarborRepositoryList(List<HarborRepository> harborRepositoryList){
		if(CollectionUtils.isEmpty(harborRepositoryList)){
			return;
		}

		//创建人ID去重，并获得创建人详细信息
		Set<Long> userIdSet = harborRepositoryList.stream().map(dto->dto.getCreatedBy()).collect(Collectors.toSet());
		ResponseEntity<List<UserDTO>> responseEntity = baseFeignClient.listUsersByIds(userIdSet.toArray(new Long[userIdSet.size()]),true);
		if(responseEntity == null){
			throw new CommonException("error.feign.user.select.empty");
		}
		Map<Long,UserDTO> userDtoMap = responseEntity.getBody().stream().collect(Collectors.toMap(UserDTO::getId,dto->dto));

		harborRepositoryList.forEach(dto->{
			//获得镜像数
			ResponseEntity<String> detailResponseEntity = harborHttpClient.exchange(HarborConstants.HarborApiEnum.DETAIL_PROJECT,null,null,false,dto.getHarborId());
			HarborProjectDTO harborProjectDTO = new Gson().fromJson(detailResponseEntity.getBody(), HarborProjectDTO.class);
			dto.setRepoCount(harborProjectDTO.getRepoCount());

			//设置创建人登录名、真实名称、创建人头像
			UserDTO userDTO = userDtoMap.get(dto.getCreatedBy());
			if(userDTO != null){
				dto.setCreatorImageUrl(userDTO.getImageUrl());
				dto.setCreatorLoginName(userDTO.getLoginName());
				dto.setCreatorRealName(userDTO.getRealName());
			}
		});
	}

	/***
	 * 保存cve白名单
	 * @param harborProjectVo
	 * @param harborProjectDTO
	 * @param harborId
	 */
	public void saveWhiteList(HarborProjectVo harborProjectVo,HarborProjectDTO harborProjectDTO,Integer harborId){
		if(HarborConstants.TRUE.equals(harborProjectVo.getUseProjectCveFlag())){
			Map<String,Object> map = new HashMap<>(4);
			List<Map<String,String >> cveMapList = new ArrayList<>();
			for(String cve : harborProjectVo.getCveNoList()){
				Map<String,String> cveMap = new HashMap<>(2);
				cveMap.put("cve_id",cve);
				cveMapList.add(cveMap);
			}
			map.put("items",cveMapList);
			map.put("expires_at",HarborUtil.dateToTimestamp(harborProjectVo.getEndDate()));
			map.put("project_id",harborId);
			map.put("id",1);
			harborProjectDTO.setCveWhiteList(map);
			harborHttpClient.exchange(HarborConstants.HarborApiEnum.UPDATE_PROJECT,null,harborProjectDTO,false,harborId);
		}
	}

	@Override
	public void saveWhiteList(HarborProjectVo harborProjectVo, Integer harborId){
		HarborProjectDTO harborProjectDTO = new HarborProjectDTO(harborProjectVo);
		saveWhiteList(harborProjectVo,harborProjectDTO,harborId);
	}

	/***
	 * 保存存储容量配置
	 * @param harborProjectVo
	 * @param harborId
	 */
	@Override
	public void saveQuota(HarborProjectVo harborProjectVo, Integer harborId){
		Integer storageLimit = HarborUtil.getStorageLimit(harborProjectVo.getStorageNum(),harborProjectVo.getStorageUnit());
		Map<String,Object> qutoaObject = new HashMap<>(1);
		Map<String,Object> hardObject = new HashMap<>(2);
		hardObject.put("count",harborProjectVo.getCountLimit());
		hardObject.put("storage",storageLimit);
		qutoaObject.put("hard",hardObject);
		harborHttpClient.exchange(HarborConstants.HarborApiEnum.UPDATE_PROJECT_QUOTA,null,qutoaObject,true,harborId);
	}

	private void checkParam(HarborProjectVo harborProjectVo){
		if(StringUtils.isEmpty(harborProjectVo.getPublicFlag())){
			harborProjectVo.setPublicFlag(HarborConstants.FALSE);
		}
		if(StringUtils.isEmpty(harborProjectVo.getContentTrustFlag())){
			harborProjectVo.setContentTrustFlag(HarborConstants.FALSE);
		}
		if(StringUtils.isEmpty(harborProjectVo.getPreventVulnerableFlag())){
			harborProjectVo.setPreventVulnerableFlag(HarborConstants.FALSE);
		}
		if(HarborConstants.TRUE.equals(harborProjectVo.getPreventVulnerableFlag())){
			if(StringUtils.isEmpty(harborProjectVo.getSeverity())){
				harborProjectVo.setSeverity(HarborConstants.SeverityLevel.LOW);
			}
		}
		if(StringUtils.isEmpty(harborProjectVo.getAutoScanFlag())){
			harborProjectVo.setAutoScanFlag(HarborConstants.FALSE);
		}
		if(harborProjectVo.getCountLimit() == null){
			harborProjectVo.setCountLimit(-1);
		}
		if(harborProjectVo.getStorageNum() == null){
			harborProjectVo.setStorageNum(-1);
		}
		if(StringUtils.isEmpty(harborProjectVo.getUseSysCveFlag())){
			harborProjectVo.setUseSysCveFlag(HarborConstants.TRUE);
			harborProjectVo.setUseProjectCveFlag(HarborConstants.FALSE);
		}

		AssertUtils.notNull(harborProjectVo.getStorageUnit(),"error.harbor.project.StorageUnit.empty");
		notIn(harborProjectVo.getStorageUnit(),"存储容量单位","error.harbor.project.StorageUnit.value.not.in",HarborConstants.KB,HarborConstants.MB,HarborConstants.GB,HarborConstants.TB);
		notIn(harborProjectVo.getPublicFlag(),"访问级别","error.harbor.project.flag.value.not.in",HarborConstants.TRUE,HarborConstants.FALSE);
		notIn(harborProjectVo.getContentTrustFlag(),"内容信任","error.harbor.project.flag.value.not.in",HarborConstants.TRUE,HarborConstants.FALSE);
		notIn(harborProjectVo.getPreventVulnerableFlag(),"阻止潜在漏洞","error.harbor.project.flag.value.not.in",HarborConstants.TRUE,HarborConstants.FALSE);
		notIn(harborProjectVo.getAutoScanFlag(),"自动扫描","error.harbor.project.flag.value.not.in",HarborConstants.TRUE,HarborConstants.FALSE);
		notIn(harborProjectVo.getUseSysCveFlag(),"启用系统白名单","error.harbor.project.flag.value.not.in",HarborConstants.TRUE,HarborConstants.FALSE);
		notIn(harborProjectVo.getUseProjectCveFlag(),"启用项目白名单","error.harbor.project.flag.value.not.in",HarborConstants.TRUE,HarborConstants.FALSE);
		notIn(harborProjectVo.getSeverity(),"危害级别","error.harbor.project.Severity.value.not.in",HarborConstants.SeverityLevel.LOW,HarborConstants.SeverityLevel.MEDIUM,HarborConstants.SeverityLevel.HIGH,HarborConstants.SeverityLevel.CRITICAL);

	}

	private void checkProject(HarborProjectVo harborProjectVo,Long projectId){
		if(CollectionUtils.isNotEmpty(harborRepositoryRepository.select(HarborRepository.FIELD_PROJECT_ID,projectId))){
			throw new CommonException("error.harbor.project.exist");
		}

		Map<String,Object> checkProjectParamMap = new HashMap<>();
		checkProjectParamMap.put("project_name",harborProjectVo.getCode());
		ResponseEntity<String> checkProjectResponse = harborHttpClient.exchange(HarborConstants.HarborApiEnum.CHECK_PROJECT_NAME,checkProjectParamMap,null,true);
		if(checkProjectResponse != null && checkProjectResponse.getStatusCode().value() == 200){
			throw new CommonException("error.harbor.project.exist");
		}
	}

	private void notIn(String str,String fieldName,String errorMsgCode,String... args){
		if(StringUtils.isEmpty(str)){
			return;
		}
		boolean flag = false;

		int length = args.length;
		for(int i=0; i < length; i++){
			if(str.equals(args[i])){
				flag = true;
				break;
			}
		}

		if(!flag){
			throw new CommonException(errorMsgCode,fieldName,str);
		}
	}

}