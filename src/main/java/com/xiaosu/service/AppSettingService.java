package com.xiaosu.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.xiaosu.domain.AppSettingRecord;
import com.xiaosu.mapper.AppSettingMapper;

@Service
public class AppSettingService {

    private final AppSettingMapper settingMapper;

    public AppSettingService(AppSettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    public void save(AppSettingRecord setting) {
        settingMapper.save(setting);
    }

    public Optional<AppSettingRecord> findByKey(String key) {
        return settingMapper.findByKey(key);
    }
}
