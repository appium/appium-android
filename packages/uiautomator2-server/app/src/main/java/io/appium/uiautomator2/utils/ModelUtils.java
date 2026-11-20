package io.appium.uiautomator2.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.appium.uiautomator2.http.IHttpRequest;
import io.appium.uiautomator2.model.api.BaseModel;

public class ModelUtils {
    public static <T extends BaseModel> T toModel(IHttpRequest request, Class<T> modelCls) {
        return toModel(request.body(), modelCls);
    }

    public static <T extends BaseModel> T toModel(String body, Class<T> modelCls) {
        return modelCls.cast(new Gson().fromJson(body, modelCls).validate());
    }

    public static String toJsonString(Object model) {
        return toJsonString(model, true);
    }

    public static String toJsonString(Object model, boolean includeNullValues) {
        GsonBuilder builder = new GsonBuilder()
                .disableHtmlEscaping();
        if (includeNullValues) {
            builder.serializeNulls();
        }
        return builder.create().toJson(model);
    }
}
