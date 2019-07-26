package com.bat.gtd.access;

import com.bat.gtd.domain.MiaoshaUser;

public class UserContext {
    private static ThreadLocal<MiaoshaUser> userHolder = new ThreadLocal<>(); // 当前线程变量

    public static void setUser(MiaoshaUser user){
        userHolder.set(user);
    }
    public static MiaoshaUser getUser(){
        return userHolder.get();
    }


}
