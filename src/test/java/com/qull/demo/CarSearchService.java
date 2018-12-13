package com.qull.demo;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/11 11:53
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class CarSearchService {
    @Autowired
    private TransportClient client;
    // es index只能小写，不能驼峰
    public static final String CAR_SALE_INDEX = "carsale";

    private static final String CAR_SALE_TYPE = "sale";

    public static void printResponse(SearchRequestBuilder request) {
        log.info("request : \n {}", request);
        SearchResponse response = request.get();
        log.info("total : {}, response : \n {}", response.getHits().getTotalHits(), response);
    }

    /**
     * 全局桶包含所有的文档，它无视查询的范围.
     * global全局桶没有参数。聚合操作针对所有的文档，忽略汽车品牌
     * part_avg_price计算基于查询范围内的所有文档，即所有的福特汽车。global_avg_price嵌套在全局桶之下，完全忽略了范围并对所有文档进行计算，聚合返回的平均值是所有汽车的平均价格
     */
    @Test
    public void aggsGlobal() {
        log.info("全局桶测试!");
        SearchRequestBuilder request = client.prepareSearch(CAR_SALE_INDEX).setTypes(CAR_SALE_TYPE)
                // 使用query查询限定范围
                .setQuery(QueryBuilders.matchQuery("make", "ford"))
                .addAggregation(AggregationBuilders.avg("part_avg_price").field("price"))
                .addAggregation(AggregationBuilders.global("global")
                        .subAggregation(AggregationBuilders.avg("global_avg_price").field("price")))
                .setSize(0);
        printResponse(request);
    }

    /**
     * 聚合范围限定还可以使用过滤。因为聚合是在查询结果范围内操作的，任何可以适用于查询的过滤器也可以应用在聚合。
     */
    @Test
    public void filterScopeAggs() {
        
    }
    
}
