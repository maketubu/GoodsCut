package com.bat.gtd.service;

import com.bat.gtd.dao.OrderDao;
import com.bat.gtd.domain.MiaoshaOrder;
import com.bat.gtd.domain.MiaoshaUser;
import com.bat.gtd.domain.OrderInfo;
import com.bat.gtd.redis.OrderKey;
import com.bat.gtd.redis.RedisService;
import com.bat.gtd.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class OrderService {
    @Autowired
    OrderDao orderDao;
    @Autowired
    RedisService redisService;

    /**
     *  根据用户id和商品id获取订单
     */
    public MiaoshaOrder getMiaoshaOrderByUserIdGoodsId(long userId, long goodsId){
        return redisService.get(OrderKey.getMiaoshaOrderByUidGid, ""+userId+"_"+goodsId, MiaoshaOrder.class);
    }

    /**
     *  根据订单id获取订单信息
     */
    public OrderInfo getOrderById(long orderId){
        return orderDao.getOrderById(orderId);
    }
    /**
     * 创建订单
     */
    @Transactional
    public OrderInfo createOrder(MiaoshaUser user, GoodsVo goods){
        // 创建订单信息
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setCreateDate(new Date());  // 日期
        orderInfo.setDeliveryAddrId(0L);
        orderInfo.setGoodsCount(1);
        orderInfo.setGoodsId(goods.getId());
        orderInfo.setGoodsName(goods.getGoodsName());
        orderInfo.setGoodsPrice(goods.getMiaoshaPrice());
        orderInfo.setOrderChannel(1);
        orderInfo.setStatus(0);
        orderInfo.setUserId(user.getId());
        // 创建秒杀订单
        MiaoshaOrder miaoshaOrder = new MiaoshaOrder();
        miaoshaOrder.setGoodsId(goods.getId());
        miaoshaOrder.setOrderId(orderInfo.getId());
        miaoshaOrder.setUserId(user.getId());
        // 插入订单
        orderDao.insertMiaoshaOrder(miaoshaOrder);
        // 订单信息存入缓存
        redisService.set(OrderKey.getMiaoshaOrderByUidGid, ""+user.getId()+"_"+goods.getId(), miaoshaOrder);

        return orderInfo;
    };

    /**
     * 删除订单
     */
    public void deleteOrders() {
        orderDao.deleteOrders();
        orderDao.deleteMiaoshaOrders();
    }

}





















