package com.xiaosu.mapper;

import java.util.Optional;

import com.xiaosu.domain.AppSettingRecord;

public interface AppSettingMapper {

    int save(AppSettingRecord setting);

    Optional<AppSettingRecord> findByKey(String key);
}
