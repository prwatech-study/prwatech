package com.prwatech.common.config;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/public")
@AllArgsConstructor
public class CachingController {

    @Autowired
    CachingService cachingService;

    @GetMapping("/clearAllCaches")
    public void clearAllCaches() {
        cachingService.evictAllCaches();
    }

    @GetMapping("/clearCache")
    @ResponseStatus(HttpStatus.OK)
    public void clearCache(@RequestParam(value = "cacheName") String cacheName) {
        cachingService.evictAllCacheValues(cacheName);
    }
}