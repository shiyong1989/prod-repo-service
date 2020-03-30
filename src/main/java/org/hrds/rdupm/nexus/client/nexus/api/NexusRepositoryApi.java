package org.hrds.rdupm.nexus.client.nexus.api;

import org.hrds.rdupm.nexus.client.nexus.model.NexusServerMavenGroup;
import org.hrds.rdupm.nexus.client.nexus.model.NexusServerMavenProxy;
import org.hrds.rdupm.nexus.client.nexus.model.NexusServerRepository;
import org.hrds.rdupm.nexus.client.nexus.model.RepositoryMavenRequest;

import java.util.List;

/**
 * 仓库API
 * @author weisen.yang@hand-china.com 2020/3/16
 */
public interface NexusRepositoryApi {

	/**
	 * 获取nexus服务,仓库信息
	 * @return List<NexusRepository>
	 */
	List<NexusServerRepository> getRepository();

	/**
	 * 获取仓库信息，通过名称
	 * @param repositoryName 仓库名称
	 * @return NexusRepository
	 */
	NexusServerRepository getRepositoryByName(String repositoryName);

	/**
	 * 仓库是否存在
	 * @param repositoryName 仓库名称
	 * @return Boolean   true：存在   false：不存在
	 */
	Boolean repositoryExists(String repositoryName);

	/**
	 * 删除仓库信息
	 * @param repositoryName 仓库名称
	 */
	void deleteRepository(String repositoryName);

	/**
	 * maven hosted仓库创建
	 * @param repositoryRequest 创建信息
	 */
	void createMavenRepository(RepositoryMavenRequest repositoryRequest);

	/**
	 * maven hosted仓库更新
	 * @param repositoryRequest 更新信息
	 */
	void updateMavenRepository(RepositoryMavenRequest repositoryRequest);

	/**
	 * maven仓库组创建与更新
	 * @param nexusMavenGroup 创建信息
	 */
	void createAndUpdateMavenGroup(NexusServerMavenGroup nexusMavenGroup);

	/**
	 * maven代理仓库创建与更新
	 * @param nexusMavenProxy 创建信息
	 */
	void createAndUpdateMavenProxy(NexusServerMavenProxy nexusMavenProxy);
}
