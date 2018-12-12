package com.qull.service.search;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BackoffPolicy;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/12 23:01
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class WebsiteSearchService {
    @Autowired
    private TransportClient client;
    
    private static final String WEBSITE_INDEX = "website";
    
    private static final String WEBSITE_TYPE = "logs";

    public BulkProcessor initBulkProcess() {
        BulkProcessor processor = BulkProcessor.builder(client, new BulkProcessor.Listener() {
            @Override
            public void beforeBulk(long executionId, BulkRequest request) {
                log.info("开始添加数据!");
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
                log.info("添加数据成功!");
            }

            @Override
            public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
                log.error("添加数据失败!");
            }
        }).setBulkActions(1000)
                .setBulkSize(new ByteSizeValue(5, ByteSizeUnit.MB))
                .setFlushInterval(TimeValue.timeValueMillis(5))
                .setConcurrentRequests(1)
                .setBackoffPolicy(BackoffPolicy.exponentialBackoff()).build();
        return processor;
    }

    private static void printResponse(SearchRequestBuilder request) {
        log.info("request : \n {}",request);
        SearchResponse response = request.get();
        log.info("total : {}, response : \n {}", response.getHits().getTotalHits(), response);
    }

    @Test
    public void importBulk() throws InterruptedException {
        BulkProcessor processor = initBulkProcess();
        processor.add(new IndexRequest().index(WEBSITE_INDEX).type(WEBSITE_TYPE).source("{\"latency\":105, \"province\":\"江苏\", \"timestamp\":\"2018-10-28\"}\n", XContentType.JSON));
        processor.add(new IndexRequest().index(WEBSITE_INDEX).type(WEBSITE_TYPE).source("{\"latency\":83, \"province\":\"江苏\", \"timestamp\":\"2018-10-29\"}\n", XContentType.JSON));
        processor.add(new IndexRequest().index(WEBSITE_INDEX).type(WEBSITE_TYPE).source("{\"latency\":92, \"province\":\"江苏\", \"timestamp\":\"2018-10-29\"}\n", XContentType.JSON));
        processor.add(new IndexRequest().index(WEBSITE_INDEX).type(WEBSITE_TYPE).source("{\"latency\":112, \"province\":\"江苏\", \"timestamp\":\"2018-10-28\"}\n", XContentType.JSON));
        processor.add(new IndexRequest().index(WEBSITE_INDEX).type(WEBSITE_TYPE).source("{\"latency\":68, \"province\":\"江苏\", \"timestamp\":\"2018-10-28\"}\n", XContentType.JSON));
        processor.add(new IndexRequest().index(WEBSITE_INDEX).type(WEBSITE_TYPE).source("{\"latency\":76, \"province\":\"江苏\", \"timestamp\":\"2018-10-29\"}\n", XContentType.JSON));
        processor.add(new IndexRequest().index(WEBSITE_INDEX).type(WEBSITE_TYPE).source("{\"latency\":101, \"province\":\"新疆\", \"timestamp\":\"2018-10-28\"}\n", XContentType.JSON));
        processor.add(new IndexRequest().index(WEBSITE_INDEX).type(WEBSITE_TYPE).source("{\"latency\":275, \"province\":\"新疆\", \"timestamp\":\"2018-10-29\"}\n", XContentType.JSON));
        processor.add(new IndexRequest().index(WEBSITE_INDEX).type(WEBSITE_TYPE).source("{\"latency\":116, \"province\":\"新疆\", \"timestamp\":\"2018-10-29\"}\n", XContentType.JSON));
        processor.add(new IndexRequest().index(WEBSITE_INDEX).type(WEBSITE_TYPE).source("{\"latency\":654, \"province\":\"新疆\", \"timestamp\":\"2018-10-28\"}\n", XContentType.JSON));
        processor.add(new IndexRequest().index(WEBSITE_INDEX).type(WEBSITE_TYPE).source("{\"latency\":389, \"province\":\"新疆\", \"timestamp\":\"2018-10-28\"}\n", XContentType.JSON));
        processor.add(new IndexRequest().index(WEBSITE_INDEX).type(WEBSITE_TYPE).source("{\"latency\":302, \"province\":\"新疆\", \"timestamp\":\"2018-10-29\"}\n", XContentType.JSON));
        processor.flush();
        Thread.sleep(6000);
        processor.close();
    }

    @Test
    public void search() {
        System.out.println(client.prepareSearch(WEBSITE_INDEX).setTypes(WEBSITE_TYPE).get());
    }


    @Test
    public void percentiles() {
//        SearchRequestBuilder request = client.prepareSearch(WEBSITE_INDEX).setTypes(WEBSITE_TYPE)
//                .addAggregation(AggregationBuilders.percentiles("latencyPercentiles")
//                        .field("latency").percentiles(50, 95, 99)
//                        .subAggregation(AggregationBuilders.avg("avgLatency").field("latency")))
//                .setSize(0);


        SearchRequestBuilder request = client.prepareSearch(WEBSITE_INDEX).setTypes(WEBSITE_TYPE)
                .addAggregation(AggregationBuilders.terms("province").field("province")
                        .subAggregation(AggregationBuilders.percentiles("latencyPercentiles").field("latency").percentiles(50, 95, 99))
                        .subAggregation(AggregationBuilders.avg("avgLatency").field("latency")))
                .setSize(0);
        printResponse(request);
    }


    @Test
    public void percentileRank() {
        log.info("统计200ms、1000ms内的访问各占多少");
        SearchRequestBuilder request = client.prepareSearch(WEBSITE_INDEX).setTypes(WEBSITE_TYPE)
                .addAggregation(AggregationBuilders.terms("province").field("province")
                        .subAggregation(AggregationBuilders.percentileRanks("percentileRanks", new double[]{200d, 1000d}).field("latency")))
                .setSize(0);
        printResponse(request);
    }


}
