package com.qull.controller;

import com.alibaba.fastjson.JSON;
import com.qull.constant.ESConstant;
import com.qull.model.ESProduct;
import com.qull.model.Product;
import com.qull.service.search.ProductSearchService;
import com.qull.service.search.SearchService;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/7 15:35
 */
@RestController
@RequestMapping("/product")
public class ProductSearchController {

    @Autowired
    private SearchService<Product> searchService;

    @Autowired
    private ProductSearchService productSearchService;

    @PostMapping("/find/{fId}")
    public Product findById(@PathVariable("fId") String fId, String fields) {
        Map<String, Object> result = searchService.searchDataById(ESConstant.ZEDAO_INDEX, ESConstant.ZEDAO_TYPE_PRODUCT, fId, fields);
        if(CollectionUtils.isEmpty(result)) {
            return null;
        }
        Product product = JSON.parseObject(JSON.toJSONString(result), Product.class);
        return product;
    }


    @PostMapping("/find")
    public List<Product> find(String keyword, String fields, ESProduct product, String page, String size) {
        SearchHits searchHits = productSearchService.searchDataByCondition(ESConstant.ZEDAO_INDEX, ESConstant.ZEDAO_TYPE_PRODUCT, keyword, fields, product, page, size);
        if(Objects.nonNull(searchHits)) {
            if(searchHits.getTotalHits() <= 0) {
                return Collections.emptyList();
            }
            List<Product> products = new ArrayList<>();
            Product productVO;
            for (SearchHit searchHit : searchHits) {
                Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();
                productVO = JSON.parseObject(JSON.toJSONString(sourceAsMap), Product.class);
                HighlightField highlightField = searchHit.getHighlightFields().get("fName");
                if(Objects.nonNull(highlightField)) {
                    Text[] titles = searchHit.getHighlightFields().get("fName").getFragments();
                    if(titles.length > 0) {
                        productVO.setFName(titles[0].toString());
                    }
                }
                products.add(productVO);
            }
            return products;
        }
        return Collections.emptyList();
    }
}
