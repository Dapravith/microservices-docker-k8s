package com.aupp.login.web;

import com.aupp.login.dto.ApiResponse;
import com.aupp.login.dto.Pagination;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Collection;

@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return !ApiResponse.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof ApiResponse<?>) {
            return body;
        }
        int status = resolveStatus(response);
        String message = defaultMessage(status);
        if (body instanceof Collection<?> coll) {
            return ApiResponse.list(status, message, body, Pagination.singlePage(coll.size()));
        }
        return ApiResponse.success(status, message, body);
    }

    private static int resolveStatus(ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse servlet) {
            int code = servlet.getServletResponse().getStatus();
            return code <= 0 ? HttpStatus.OK.value() : code;
        }
        return HttpStatus.OK.value();
    }

    private static String defaultMessage(int status) {
        HttpStatus s = HttpStatus.resolve(status);
        return s == null ? "OK" : s.getReasonPhrase();
    }
}
