package com.bat.gtd.access;

import com.alibaba.fastjson.JSON;
import com.bat.gtd.domain.MiaoshaUser;
import com.bat.gtd.redis.AccessKey;
import com.bat.gtd.redis.RedisService;
import com.bat.gtd.result.CodeMsg;
import com.bat.gtd.result.Result;
import com.bat.gtd.service.MiaoshaUserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;

/**
 *  获取拦截器
 */
@Service
public class AccessInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    MiaoshaUserService userService;

    @Autowired
    RedisService redisService;

    /**
     *  在处理业务请求前执行，如果当前用户没有登陆，则拒绝并返回错误信息，
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws Exception {
        if (handler instanceof HandlerMethod) {  //如果是一个web处理器方法，即处理一个web请求
            MiaoshaUser user = getUser(request, response);
            UserContext.setUser(user); // 设置一个线程变量 user
            HandlerMethod hm = (HandlerMethod)handler;
            AccessLimit accessLimit = hm.getMethodAnnotation(AccessLimit.class);
            if(accessLimit == null){
                return true;
            }
            int seconds = accessLimit.seconds();   //  设置过期时间
            int maxCount = accessLimit.maxCount();  // 限制请求次数
            boolean needLogin = accessLimit.needLogin(); //是否需要登陆
            String key = request.getRequestURI();  // 请求url
            if(needLogin) {
                if(user == null){
                    // 如果对象为空，则写入错误信息，表示需要登陆！
                    render(response, CodeMsg.SESSION_ERROR);
                    return false;
                }
                key += "_" + user.getId();
            }else{
                // do  nothing
            }

            AccessKey ak = AccessKey.withExpire(seconds);  // 获得一个过期时间位seconds的AccessKey对象
            Integer count = redisService.get(ak, key, Integer.class);  // 从redis中获取请求获取的次数
            if(count == null) {
                redisService.set(ak, key, 1); // redis中没有，则添加进去
            }else if(count < maxCount){
                redisService.incr(ak, key);
            } else {
                render(response, CodeMsg.ACCESS_LIMIT_REACHED);
                return false;
            }
        }
        return true;
    };

    /**
     * render ,将错误信息写入response输出流
     */
    private void render(HttpServletResponse response, CodeMsg cm) throws Exception{
        response.setContentType("application/json;charset=UTF-8");
        OutputStream out  = response.getOutputStream();
        String str = JSON.toJSONString(Result.error(cm));
        out.write(str.getBytes("UTF-8"));
        out.flush();
        out.close();
    }


    /**
     * 从 session中获取 当前的用户信息
     */
    private MiaoshaUser getUser(HttpServletRequest request, HttpServletResponse response){
        // 从session 中获取当前用户的token
        String paramToken = request.getParameter(MiaoshaUserService.COOKIE_NAME_TOKEN);
        String cookieToken = getCookieValue(request, MiaoshaUserService.COOKIE_NAME_TOKEN);
        if(StringUtils.isEmpty(cookieToken) && StringUtils.isEmpty(paramToken)){
            return null;
        }
        String token = StringUtils.isEmpty(paramToken) ? cookieToken: paramToken;
        return userService.getByToken(response, token);  // 从redis中获取，获取不到则从数据库中获取并保存到会话中
    };

    /**
     *  从cookie中获取值，即根据cookie的name，获取value
     */
    private String getCookieValue(HttpServletRequest request, String cookieName){
        // 从会话中获取所有cookie
        Cookie[] cookies = request.getCookies();
        if(cookies == null || cookies.length <= 0){
            return null;
        }
        // 遍历检查所有的cookie
        for(Cookie cookie : cookies){
            if(cookie.getName().equals(cookieName)){
                return cookie.getValue();
            }
        }
        return null;
    };

}

























