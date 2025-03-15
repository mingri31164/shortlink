package com.mingri.shortlink.admin.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mingri.shortlink.admin.common.biz.user.UserContext;
import com.mingri.shortlink.admin.common.convention.exception.ClientException;
import com.mingri.shortlink.admin.common.convention.exception.ServiceException;
import com.mingri.shortlink.admin.dao.entity.GroupDO;
import com.mingri.shortlink.admin.dao.entity.GroupUniqueDO;
import com.mingri.shortlink.admin.dao.mapper.GroupMapper;
import com.mingri.shortlink.admin.dao.mapper.GroupUniqueMapper;
import com.mingri.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.mingri.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.mingri.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.mingri.shortlink.admin.service.GroupService;
import com.mingri.shortlink.admin.toolkit.RandomGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.mingri.shortlink.admin.common.constant.RedisCacheConstant.LOCK_GROUP_CREATE_KEY;


/**
 * 短链接分组接口实现层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

    private final RBloomFilter<String> gidRegisterCachePenetrationBloomFilter;
    private final GroupUniqueMapper groupUniqueMapper;
    private final RedissonClient redissonClient;

    @Value("${short-link.group.max-num}")
    private Integer groupMaxNum;

    @Override
    public void saveGroup(String groupName) {
        saveGroup(UserContext.getUsername(), groupName);
    }

    @Override
    public void saveGroup(String username, String groupName) {
        RLock lock = redissonClient.getLock(String.format(LOCK_GROUP_CREATE_KEY, username));
        lock.lock();
        try {
            LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                    .eq(GroupDO::getUsername, username)
                    .eq(GroupDO::getDelFlag, 0);
            List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);
            if (CollUtil.isNotEmpty(groupDOList) && groupDOList.size() == groupMaxNum) {
                throw new ClientException(String.format("已超出最大分组数：%d", groupMaxNum));
            }
            int retryCount = 0;
            int maxRetries = 10;
            String gid = null;
            while (retryCount < maxRetries) {
                gid = saveGroupUniqueReturnGid();
                if (StrUtil.isNotEmpty(gid)) {
                    GroupDO groupDO = GroupDO.builder()
                            .gid(gid)
                            .sortOrder(0)
                            .username(username)
                            .name(groupName)
                            .build();
                    baseMapper.insert(groupDO);
                    gidRegisterCachePenetrationBloomFilter.add(gid);
                    break;
                }
                retryCount++;
            }
            if (StrUtil.isEmpty(gid)) {
                throw new ServiceException("生成分组标识频繁");
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ShortLinkGroupRespDTO> listGroup() {
        // TODO 查询用户短链接分组集合
        return null;
    }


    @Override
    public void updateGroup(ShortLinkGroupUpdateReqDTO requestParam) {
        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, requestParam.getGid())
                .eq(GroupDO::getDelFlag, 0);
        GroupDO groupDO = new GroupDO();
        groupDO.setName(requestParam.getName());
        baseMapper.update(groupDO, updateWrapper);
    }

    @Override
    public void deleteGroup(String gid) {
        LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getDelFlag, 0);
        GroupDO groupDO = new GroupDO();
        groupDO.setDelFlag(1);
        baseMapper.update(groupDO, updateWrapper);
    }

    @Override
    public void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam) {
        requestParam.forEach(each -> {
            GroupDO groupDO = GroupDO.builder()
                    .sortOrder(each.getSortOrder())
                    .build();
            LambdaUpdateWrapper<GroupDO> updateWrapper = Wrappers.lambdaUpdate(GroupDO.class)
                    .eq(GroupDO::getUsername, UserContext.getUsername())
                    .eq(GroupDO::getGid, each.getGid())
                    .eq(GroupDO::getDelFlag, 0);
            baseMapper.update(groupDO, updateWrapper);
        });
    }

    private String saveGroupUniqueReturnGid() {
        String gid = RandomGenerator.generateRandom();
        if (gidRegisterCachePenetrationBloomFilter.contains(gid)) {
            return null;
        }
        GroupUniqueDO groupUniqueDO = GroupUniqueDO.builder()
                .gid(gid)
                .build();
        try {
            groupUniqueMapper.insert(groupUniqueDO);
        } catch (DuplicateKeyException e) {
            return null;
        }
        return gid;
    }
}
