package com.prwatech.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class CachingService {

    @Autowired
    CacheManager cacheManager;

    public void evictAllCacheValues(String cacheName) {
        cacheManager.getCache(cacheName).clear();
    }
    public void evictAllCaches() {
        cacheManager.getCacheNames().stream()
                .forEach(cacheName -> cacheManager.getCache(cacheName).clear());
    }
}
