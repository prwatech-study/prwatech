package com.prwatech.common.config;

import com.google.gson.Gson;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class CustomKeyGenerator implements KeyGenerator {

    @Autowired
    private Gson gson;

    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (params.length == 0) {
            return new SimpleKey(target.getClass().getName(), method).toString();
        }

        JSONArray paramsKey;
        try {
            paramsKey = new JSONArray(gson.toJson(params));

        } catch (Exception e) {
            Object paramsKeyObject;
            try {
                paramsKeyObject = gson.toJson(params);
            } catch (Exception exe) {
                paramsKeyObject = params;
            }
            return new SimpleKey(target.getClass().getName(), method, paramsKeyObject).toString();
        }

        try {
            if (paramsKey.getJSONObject(0) != null
                    && (!paramsKey.getJSONObject(0).isNull("module")
                    || !paramsKey.getJSONObject(0).isNull("moduleName")
                    || !paramsKey.getJSONObject(0).isNull("source"))) {
                paramsKey.getJSONObject(0).remove("module");
                paramsKey.getJSONObject(0).remove("moduleName");
                paramsKey.getJSONObject(0).remove("source");
            }
        } catch (Exception e) {
        }

        return new SimpleKey(target.getClass().getName(), method, paramsKey.toString()).toString();
    }
}

