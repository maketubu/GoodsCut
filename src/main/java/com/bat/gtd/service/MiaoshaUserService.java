package com.bat.gtd.service;

import com.bat.gtd.dao.MiaoshaUserDao;
import com.bat.gtd.domain.MiaoshaUser;
import com.bat.gtd.exception.GlobalException;
import com.bat.gtd.redis.MiaoshaUserKey;
import com.bat.gtd.redis.RedisService;
import com.bat.gtd.result.CodeMsg;
import com.bat.gtd.util.MD5Util;
import com.bat.gtd.util.UUIDUtil;
import com.bat.gtd.vo.LoginVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.security.acl.LastOwnerException;

@Service
public class MiaoshaUserService {
    // redis类型
    public static final String COOKIE_NAME_TOKEN = "token";

    @Autowired
    MiaoshaUserDao miaoshaUserDao;

    @Autowired
    RedisService redisService;

    public MiaoshaUser getById(long id) {
        // 首先从缓存中取
        MiaoshaUser user = redisService.get(MiaoshaUserKey.getById, ""+id, MiaoshaUser.class);
        if(user != null){
            return user;
        }
        // 没获取到，则从数据库中取
        user = miaoshaUserDao.getById(id);
        if (user != null){
            redisService.set(MiaoshaUserKey.getById, ""+id, user);
        }
        return  user;
    };

    /**
    *  更新密码
     */
    // 数据库更新号如何更新缓存  http://blog.csdn.net/tTU1EvLDeLFq5btqiK/article/details/78693323
    public boolean updatePassword(String token, long id, String formPass){
        // 首先获取用户
        MiaoshaUser user = getById(id);
        if (user == null){
            throw new GlobalException(CodeMsg.MOBILE_NOT_EXIST);
        }
        // 更新数据库
        MiaoshaUser toBeUpdate = new MiaoshaUser(); // 创建一个新的user
        toBeUpdate.setId(id);
        toBeUpdate.setPassword(MD5Util.formPassToDBPass(formPass, user.getSalt()));
        miaoshaUserDao.update(toBeUpdate);  //根据用户id更新密码
        // 处理缓存
        redisService.delete(MiaoshaUserKey.getById, ""+id);
        user.setPassword(toBeUpdate.getPassword());  //更新缓存中的user密码
        redisService.set(MiaoshaUserKey.token, token, user);
        return true;
    };

    /**
     * 获取当前用户
     * @param response
     * @param token
     * @return
     */
    // 获取当前用户
    public MiaoshaUser getByToken(HttpServletResponse response, String token){
        if(StringUtils.isEmpty(token)) {
            return null;
        }
        MiaoshaUser user = redisService.get(MiaoshaUserKey.token, token, MiaoshaUser.class);
        // 延长有效期
        if(user != null){
            addCookie(response, token, user);
        }
        return user;
    };

    /**
     * 登陆服务,返回当前user的redis 的 标识符
     */
    public String login(HttpServletResponse response, LoginVo loginVo){
        if(loginVo == null){
            throw new GlobalException(CodeMsg.SERVER_ERROR);
        }
        String mobile = loginVo.getMobile();
        String formPass = loginVo.getPassword();
        // 验证手机号是否存在
        MiaoshaUser user = getById(Long.parseLong(mobile));
        if(user == null){
            throw new GlobalException(CodeMsg.MOBILE_NOT_EXIST);
        }
        //验证密码
        String dbPass = user.getPassword();
        String saltDB = user.getSalt();
        String calcPass = MD5Util.formPassToDBPass(formPass, saltDB);
        if(!calcPass.equals(dbPass)) {
            throw new GlobalException(CodeMsg.PASSWORD_ERROR);
        }
        // 生成cookie
        String token = UUIDUtil.uuid();
        addCookie(response, token, user);
        return token;
    }

    /**
    * 添加cookie，存缓存
     */
    public void addCookie(HttpServletResponse response, String token, MiaoshaUser user){
        redisService.set(MiaoshaUserKey.token, token, user);
        Cookie cookie = new Cookie(COOKIE_NAME_TOKEN, token);
        cookie.setMaxAge(MiaoshaUserKey.token.expireSeconds());
        cookie.setPath("/");
        response.addCookie(cookie);
    };
}
