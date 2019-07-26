package com.bat.gtd.config;

import com.bat.gtd.access.UserContext;
import com.bat.gtd.domain.MiaoshaUser;
import com.bat.gtd.service.MiaoshaUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Service
public class UserArgumentResolver implements HandlerMethodArgumentResolver {
    @Autowired
    MiaoshaUserService miaoshaUserService;

    /**
     * 判断参数类型是否相同
     * @param parameter
     * @return
     */
    public boolean supportsParameter(MethodParameter parameter){
        // 获取参数类型
        Class<?> clazz = parameter.getParameterType();
        return clazz == MiaoshaUser.class;
    };
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        return UserContext.getUser();
    };
}
