package com.qull.service.search;

import com.qull.model.ESProduct;
import org.elasticsearch.search.SearchHits;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/7 10:01
 */
public interface ProductSearchService {
    /**
     * 根据条件查询数据
     * @param index
     * @param type
     * @param keyword
     * @param fields
     * @param product
     * @return
     */
    SearchHits searchDataByCondition(String index, String type, String keyword, String fields, ESProduct product, String page, String size);
}
