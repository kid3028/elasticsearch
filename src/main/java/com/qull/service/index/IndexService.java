package com.qull.service.index;

import com.qull.model.Product;

import java.util.List;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/3 13:52
 */
public interface IndexService<T> {

    /**
     * 创建索引
     * @param index
     * @return
     */
    boolean createIndex(String index);

    /**
     * 删除索引
     * @param index
     * @return
     */
    boolean deleteIndex(String index);


    boolean importBulk(String index, String type, List<Product> dataList);


    /**
     * 判断索引是否存在
     * @param index
     * @return
     */
    boolean isIndexExists(String index);

    /**
     * 查询index下是否存在某个type
     * @param index
     * @param type
     * @return
     */
    boolean isTypeExists(String index, String type);

}
