package com.bat.gtd.controller;

import com.bat.gtd.redis.RedisService;
import com.bat.gtd.result.Result;
import com.bat.gtd.service.MiaoshaUserService;
import com.bat.gtd.vo.LoginVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import sun.security.jgss.HttpCaller;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;


@Controller
@RequestMapping("/login")
public class LoginController {
    private static Logger log = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    MiaoshaUserService miaoshaUserService;

    @Autowired
    RedisService redisService;

    /**
     *  重
     */
    @RequestMapping("/to_login")
    public String toLogin(){
        return "login";
    }

    /**
     *  实际登陆界面
     */
    @RequestMapping("/do_login")
    @ResponseBody
    public Result<String> doLogin(HttpServletResponse response, @Valid LoginVo loginVo){
        log.info(loginVo.toString());
        //登陆
        String token = miaoshaUserService.login(response, loginVo);
        return Result.success(token);
    }

}


























