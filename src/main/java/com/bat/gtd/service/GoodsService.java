package com.bat.gtd.service;

import com.bat.gtd.dao.GoodsDao;
import com.bat.gtd.domain.MiaoshaGoods;
import com.bat.gtd.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoodsService {

    @Autowired
    GoodsDao goodsDao;

    public List<GoodsVo> listGoodsVo(){
        return goodsDao.listGoodsVo();
    }

    /**
     *  获取id获取商品
     */
    public GoodsVo getGoodsVoByGoodsId(long goodsId){
        return goodsDao.getGoodsVoByGoodsId(goodsId);
    }

    /**
     *  减少商品库存
     */
    public boolean reduceStock(GoodsVo goodsVo){
        MiaoshaGoods g = new MiaoshaGoods();
        g.setGoodsId(goodsVo.getId());
        int ret = goodsDao.reduceStock(g);
        return ret > 0;
    }

    /**
     *  设置商品库存
     */
    public void resetStock(List<GoodsVo> goodsList){
        for(GoodsVo goods : goodsList){
            MiaoshaGoods g = new MiaoshaGoods();
            g.setGoodsId(goods.getId());  // 设置商品id
            g.setStockCount(goods.getStockCount());  //设置商品库存
            goodsDao.resetStock(g);
        }
    }
}











