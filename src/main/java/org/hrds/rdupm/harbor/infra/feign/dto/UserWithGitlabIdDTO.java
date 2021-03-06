package org.hrds.rdupm.harbor.infra.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;

/**
 * @author zmf
 * @since 20-3-18
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserWithGitlabIdDTO extends UserDTO {

    @ApiModelProperty("用户对应的gitlab用户id")
    private Long gitlabUserId;

    public Long getGitlabUserId() {
        return gitlabUserId;
    }

    public void setGitlabUserId(Long gitlabUserId) {
        this.gitlabUserId = gitlabUserId;
    }
}
