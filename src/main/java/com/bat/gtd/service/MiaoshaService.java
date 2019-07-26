package com.bat.gtd.service;

import com.bat.gtd.domain.MiaoshaOrder;
import com.bat.gtd.domain.MiaoshaUser;
import com.bat.gtd.domain.OrderInfo;
import com.bat.gtd.redis.MiaoshaKey;
import com.bat.gtd.redis.RedisService;
import com.bat.gtd.util.MD5Util;
import com.bat.gtd.util.UUIDUtil;
import com.bat.gtd.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

@SuppressWarnings("restriction")
@Service
public class MiaoshaService {

    @Autowired
    GoodsService goodsService;

    @Autowired
    OrderService orderService;

    @Autowired
    RedisService redisService;

    /**
     *  减库存， 下订单， 写入秒杀订单
     */
    @Transactional
    public OrderInfo miaosha(MiaoshaUser user, GoodsVo goods){
        boolean success = goodsService.reduceStock(goods);  // 库存-1
        if(success){
            // 库存-1成功，则下一个订单
            return orderService.createOrder(user, goods);
        }else {
            // 失败，商品售空
            setGoodsOver(goods.getId());
            return null;
        }
    };

    public long getMiaoshaResult(Long userId, long goodsId){
        //获取订单
        MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(userId,goodsId);
        if(order != null){
            return order.getOrderId();  //返回订单id
        }else {
            boolean isOver = getGoodsOver(goodsId);  // 判断是否商品售空
            if(isOver){
                return -1;  // 售空返回1
            }else {
                return 0;  //没有则返回0
            }
        }
    };

    /**
     * 设置商品售空
     */
    private void setGoodsOver(Long goodsId){
        // 在redis中设置该商品已经卖空
        redisService.set(MiaoshaKey.isGoodsOver, ""+goodsId, true);
    }
    /**
     *  判断是否是售空
     */
    private boolean getGoodsOver(long goodsId){
        return redisService.exists(MiaoshaKey.isGoodsOver, ""+goodsId);
    }
    /**
     *  重置商品的库存
     */
    public void reset(List<GoodsVo> goodsList){
        goodsService.resetStock(goodsList);
        orderService.deleteOrders();
    }
    /**
     * 检查路径？
     */
    public boolean checkPath(MiaoshaUser user, long goodsId, String path) {
        if(user == null || path == null) {
            return false;
        }
        String pathOld = redisService.get(MiaoshaKey.getMiaoshaPath, ""+user.getId() + "_"+ goodsId, String.class);
        return path.equals(pathOld);
    }
    /**
     * 创建秒杀路径
     */
    public String createMiaoshaPath(MiaoshaUser user, long goodsId){
        if(user == null || goodsId <= 0 ){
            return null;
        }
        String str = MD5Util.md5(UUIDUtil.uuid() + "123456");
        redisService.set(MiaoshaKey.getMiaoshaPath, ""+user.getId()+"_"+goodsId, str);
        return str;
    }

    /**
     * 创建图片验证码
     */
    public BufferedImage createVerfyCode(MiaoshaUser user, long goodsId){
        if(user == null || goodsId <=0) {
            return null;
        }
        int width = 80;
        int height = 32;
        //create the image
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        // set the background color
        g.setColor(new Color(0xDCDCDC));
        g.fillRect(0, 0, width, height);
        // draw the border
        g.setColor(Color.black);
        g.drawRect(0, 0, width - 1, height - 1);
        // create a random instance to generate the codes
        Random rdm = new Random();
        // make some confusion
        for (int i = 0; i < 50; i++) {
            int x = rdm.nextInt(width);
            int y = rdm.nextInt(height);
            g.drawOval(x, y, 0, 0);
        }
        // generate a random code
        String verifyCode = generateVerifyCode(rdm);
        g.setColor(new Color(0, 100, 0));
        g.setFont(new Font("Candara", Font.BOLD, 24));
        g.drawString(verifyCode, 8, 24);
        g.dispose();
        //把验证码存到redis中
        int rnd = calc(verifyCode);
        redisService.set(MiaoshaKey.getMiaoshaVerifyCode, user.getId()+","+goodsId, rnd);
        //输出图片
        return image;
    }

    /**
     *  检查验证码
     */
    public boolean checkVertiyCode(MiaoshaUser user, long goodsId, int vertifyCode){
        if(user == null || goodsId <=0 ){
            return false;
        }
        // 从redis中获取验证码
        Integer codeOld = redisService.get(MiaoshaKey.getMiaoshaVerifyCode, user.getId()+ ","+goodsId, Integer.class);
        if(codeOld == null || codeOld - vertifyCode != 0){
            return false;  //验证失败
        }
        redisService.delete(MiaoshaKey.getMiaoshaVerifyCode, user.getId()+","+goodsId);
        return true;
    };

    /**
     *  日历
     */
    private static int calc(String exp) {
        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");
            return (Integer)engine.eval(exp);
        }catch(Exception e) {
            e.printStackTrace();
            return 0;
        }
    };

    /**
     *   产生验证码
     */
    private static char[] ops = new char[] {'+', '-', '*'};
    private String generateVerifyCode(Random rdm){
        int num1 = rdm.nextInt(10);
        int num2 = rdm.nextInt(10);
        int num3 = rdm.nextInt(10);
        char op1 = ops[rdm.nextInt(3)];
        char op2 = ops[rdm.nextInt(3)];
        String exp = ""+ num1 + op1 + num2 + op2 + num3;
        return exp;
    }
}





















