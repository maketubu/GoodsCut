package com.bat.gtd.rabbitmq;

import com.bat.gtd.domain.MiaoshaOrder;
import com.bat.gtd.domain.MiaoshaUser;
import com.bat.gtd.redis.RedisService;
import com.bat.gtd.service.GoodsService;
import com.bat.gtd.service.MiaoshaService;
import com.bat.gtd.service.OrderService;
import com.bat.gtd.vo.GoodsVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *  消费者
 */
@Service
public class MQReceiver {
    private static Logger log = LoggerFactory.getLogger(MQReceiver.class);

    @Autowired
    RedisService redisService;

    @Autowired
    GoodsService goodsService;

    @Autowired
    OrderService orderService;

    @Autowired
    MiaoshaService miaoshaService;

    /**
     *  消息回发,指定的是秒杀队列，保证不会出现废弃消息
     */
    @RabbitListener(queues = MQConfig.MIAOSHA_QUEUE)
    public void receive(String message){
        log.info("receive message:"+message);
        MiaoshaMessage mm = RedisService.stringToBean(message, MiaoshaMessage.class); //
        MiaoshaUser user = mm.getUser();
        long goodsId = mm.getGoodsId();
        // 获取商品
        GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
        // 判断商品库存
        int stock = goods.getStockCount();
        if(stock <= 0){
            return;
        }
        // 判断是否已经秒杀成功 防止重复下单
        MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsId);
        if(order != null){
            return;  // 已经秒杀成功
        }
        miaoshaService.miaosha(user, goods);
    }

}








