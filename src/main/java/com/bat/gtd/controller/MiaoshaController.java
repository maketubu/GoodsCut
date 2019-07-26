package com.bat.gtd.controller;

import com.bat.gtd.access.AccessLimit;
import com.bat.gtd.domain.MiaoshaOrder;
import com.bat.gtd.domain.MiaoshaUser;
import com.bat.gtd.rabbitmq.MQSender;
import com.bat.gtd.rabbitmq.MiaoshaMessage;
import com.bat.gtd.redis.GoodsKey;
import com.bat.gtd.redis.MiaoshaKey;
import com.bat.gtd.redis.OrderKey;
import com.bat.gtd.redis.RedisService;
import com.bat.gtd.result.CodeMsg;
import com.bat.gtd.result.Result;
import com.bat.gtd.service.GoodsService;
import com.bat.gtd.service.MiaoshaService;
import com.bat.gtd.service.MiaoshaUserService;
import com.bat.gtd.service.OrderService;
import com.bat.gtd.vo.GoodsVo;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

@Controller
@RequestMapping("/miaosha")
public class MiaoshaController implements InitializingBean {

    @Autowired
    MiaoshaUserService userService;

    @Autowired
    RedisService redisService;

    @Autowired
    GoodsService goodsService;

    @Autowired
    OrderService orderService;

    @Autowired
    MiaoshaService miaoshaService;

    @Autowired
    MQSender sender;

    // 商品id ，true表示超卖
    private HashMap<Long, Boolean> localOverMap = new HashMap<Long, Boolean>();

    /**
     * 系统初始化
     */
    public void afterPropertiesSet() throws Exception {
        // 获取商品列表
        List<GoodsVo> goodsList = goodsService.listGoodsVo();
        if(goodsList == null){
            return;
        }
        for(GoodsVo goods : goodsList){
            // 将所有商品缓存
            redisService.set(GoodsKey.getMiaoshaGoodsStock, ""+goods.getId(), goods.getStockCount());
            localOverMap.put(goods.getId(), false);
        }
    }

    /**
     *  重置库存
     */
    @RequestMapping(value = "/reset", method = RequestMethod.GET)
    @ResponseBody
    public Result<Boolean> reset(Model model){
        List<GoodsVo> goodsList = goodsService.listGoodsVo();
        // 重置每个商品的库存
        for(GoodsVo goods : goodsList){
            goods.setStockCount(10);
            redisService.set(GoodsKey.getMiaoshaGoodsStock, ""+goods.getId(), 10);
        }
        // 删除redis中的
        redisService.delete(OrderKey.getMiaoshaOrderByUidGid);
        redisService.delete(MiaoshaKey.isGoodsOver);
        // 数据库更新
        miaoshaService.reset(goodsList);
        return Result.success(true);
    }

    /**
     *   秒杀请求
     */
    @RequestMapping(value = "/{path}/do_miaosha", method = RequestMethod.POST)
    @ResponseBody
    public Result<Integer> miaosha(Model model, MiaoshaUser user, @RequestParam("goodsId") long goodsId,
                                   @PathVariable("path") String path){
        model.addAttribute("user", user);
        if(user == null){
            return Result.error(CodeMsg.SESSION_ERROR);
        }
        // 验证path
        boolean check =miaoshaService.checkPath(user, goodsId, path);
        if(!check){
            return Result.error(CodeMsg.REQUEST_ILLEGAL);
        }
        // 内存标记，减少redis访问
        boolean over = localOverMap.get(goodsId);
        if (over){
            return Result.error(CodeMsg.MIAO_SHA_OVER);
        }
        // redis预减库存
        long stock = redisService.decr(GoodsKey.getMiaoshaGoodsStock, ""+goodsId); // 默认为10
        if(stock < 0){
            // 超卖
            localOverMap.put(goodsId, true);
            return Result.error(CodeMsg.MIAO_SHA_OVER);
        }
        // 判断是否已经完成秒杀
        MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsId);
        if(order != null){
            return Result.error(CodeMsg.REPEATE_MIAOSHA);
        }
        //入队
        MiaoshaMessage mm = new MiaoshaMessage();
        mm.setUser(user);
        mm.setGoodsId(goodsId);
        sender.sendMiaoshaMessage(mm);
        return Result.success(0);//排队中

    };

    /**
     *  秒杀结果页面
     */
    @RequestMapping(value="/result", method=RequestMethod.GET)
    @ResponseBody
    public Result<Long> miaoshaResult(Model model,MiaoshaUser user,
                                      @RequestParam("goodsId")long goodsId) {
        model.addAttribute("user",user);
        // 如果用户没有登陆
        if(user == null){
            return Result.error(CodeMsg.SESSION_ERROR);
        }
        // 根据用户id和商品id获取结果
        long result = miaoshaService.getMiaoshaResult(user.getId(), goodsId);
        return Result.success(result);
    }
    /**
     *  获取秒杀路径，需要登陆
     */
    @AccessLimit(seconds = 5, maxCount = 5, needLogin = true)
    @RequestMapping(value = "/path", method = RequestMethod.GET)
    @ResponseBody
    public Result<String> getMiaoshaPath(HttpServletRequest request, MiaoshaUser user,
                                         @RequestParam("goodsId")long goodsId,
                                         @RequestParam(value = "vertifyCode", defaultValue = "0") int vertifyCode){
        if(user == null){
            return Result.error(CodeMsg.SESSION_ERROR);
        }
        // 检查验证码
        boolean check = miaoshaService.checkVertiyCode(user, goodsId, vertifyCode);
        if(!check){ // 验证失败
            return Result.error(CodeMsg.REQUEST_ILLEGAL);
        }
        String path = miaoshaService.createMiaoshaPath(user, goodsId);
        return Result.success(path);
    };
    /**
     * 图片验证请求
     */
    @RequestMapping(value="/verifyCode", method=RequestMethod.GET)
    @ResponseBody
    public Result<String> getMiaoshaVerifyCod(HttpServletResponse response, MiaoshaUser user,
                                              @RequestParam("goodsId")long goodsId) {
        if(user == null) {
            return Result.error(CodeMsg.SESSION_ERROR);
        }
        try {
            // 获取验证土坯那
            BufferedImage image  = miaoshaService.createVerfyCode(user, goodsId);
            // 写入session
            OutputStream out = response.getOutputStream();
            ImageIO.write(image, "JPEG", out);
            out.flush();
            out.close();
            return null;
        }catch(Exception e) {
            e.printStackTrace();
            return Result.error(CodeMsg.MIAOSHA_FAIL);
        }
    }

}











