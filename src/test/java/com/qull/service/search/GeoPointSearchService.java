package com.qull.service.search;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/13 21:10
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class GeoPointSearchService {

    @Autowired
    private TransportClient client;

    private static final String GEO_INDEX = "geo";

    private static final String GEO_TYPE = "geo_type";

    private static void printResponse(SearchRequestBuilder request) {
        log.info("request : \n {}",request);
        SearchResponse response = request.get();
        log.info("total : {}, response : \n {}", response.getHits().getTotalHits(), response);
    }

    /**
     * 第一个地理位置的数据类型，geoPoint。一个地理位置坐标点，包含了一个精度，一个维度，就可以唯一定位一个地球上的坐标
     */
    @Test
    public void geoPointObject() {
        log.info("使用经度和纬度对象写入地理位置!");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("text", "Geo-point as an object");
        Map<String, Double> location = new HashMap<>();
        location.put("lat", 41.32); //纬度
        location.put("lon", -71.34);   // 经度
        jsonObject.put("location", location);
        IndexRequestBuilder request = client.prepareIndex(GEO_INDEX, GEO_TYPE).setSource(jsonObject.toJSONString(), XContentType.JSON);
        log.info("request : " + request );
        IndexResponse response = request.get();
        log.info("response : " + response);
    }

    @Test
    public void geoPointString() {
        log.info("使用经纬度字符串写入地理位置");
        IndexRequestBuilder request = client.prepareIndex(GEO_INDEX, GEO_TYPE).setSource("{\"text\": \"Geo-point as a String\", \"location\":\"41.2, -71.34\"}", XContentType.JSON);
        log.info("request : " + request );
        IndexResponse response = request.get();
        log.info("response : " + response);
    }

    @Test
    public void geoPointArray() {
        log.info("使用经纬度数组写入地理位置");
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("text", "Geo-point as an Array");
        jsonObject.put("location", new double[]{41.32, -71.34});
        IndexRequestBuilder request = client.prepareIndex(GEO_INDEX, GEO_TYPE).setSource(jsonObject, XContentType.JSON);
        log.info("request : " + request );
        IndexResponse response = request.get();
        log.info("response : " + response);
    }


    @Test
    public void search() {
        SearchRequestBuilder request = client.prepareSearch(GEO_INDEX).setTypes(GEO_TYPE)
                .setQuery(QueryBuilders.geoBoundingBoxQuery("location").setCorners(42, -72, 40, -74));
        printResponse(request);
    }


    /**
     * {
     * 	"mappings":{
     * 		"hotels":{
     * 			"properties":{
     * 				"pin":{
     * 					"properties":{
     * 						"location":{
     * 							"type":"geo_point"
     *                                 }
     *                               }
     * 				}
     * 			}
     * 		}
     * 	}
     * }
     */

    private static final String HOTEL_INDEX = "hotel_app";
    
    private static final String HOTEL_TYPE = "hotels";

    @Test
    public void addData() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("name", "喜来登大酒店");
        Map<String, Double> location = new HashMap<>(2);
        location.put("lat", 40.12);
        location.put("lon", -71.34);
        JSONObject locations = new JSONObject();
        locations.put("location", location);
        jsonObject.put("pin", locations);
        IndexRequestBuilder request = client.prepareIndex().setIndex(HOTEL_INDEX).setType(HOTEL_TYPE)
                .setSource(jsonObject, XContentType.JSON);
        IndexResponse response = request.get();
        log.info("request : \n {}, \n response : \n {}", request, response);
    }

    @Test
    public void boundingBox() {
        SearchRequestBuilder request = client.prepareSearch(HOTEL_INDEX).setTypes(HOTEL_TYPE)
                .setQuery(QueryBuilders.boolQuery().must(QueryBuilders.matchAllQuery())
                        .filter(QueryBuilders.geoBoundingBoxQuery("pin.location").setCorners(40.73, -74.1, 40.01, -71.12)));
        printResponse(request);
    }

    @Test
    public void polygon() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(40.73, -74.1));
        points.add(new GeoPoint(40.01, -71.12));
        points.add(new GeoPoint(50.56, -90.58));
        SearchRequestBuilder request = client.prepareSearch(HOTEL_INDEX).setTypes(HOTEL_TYPE)
                .setQuery(QueryBuilders.boolQuery().must(QueryBuilders.matchAllQuery())
                        .filter(QueryBuilders.geoPolygonQuery("pin.location", points)));
        printResponse(request);
    }

    @Test
    public void distance() {
        log.info("搜索距离当前位置一定距离的 酒店");
        SearchRequestBuilder request = client.prepareSearch(HOTEL_INDEX).setTypes(HOTEL_TYPE)
                .setQuery(QueryBuilders.boolQuery().must(QueryBuilders.matchAllQuery())
                        .filter(QueryBuilders.geoDistanceQuery("pin.location")
                                .distance("20", DistanceUnit.KILOMETERS).point(40.1, -71.4)));
        printResponse(request);
    }


    @Test
    public void distanceRange() {
        log.info("统计当前地址位置每个距离内有多少酒店");
        SearchRequestBuilder request = client.prepareSearch(HOTEL_INDEX).setTypes(HOTEL_TYPE)
                .addAggregation(AggregationBuilders.geoDistance("aggByDistance", new GeoPoint(40.11, -71.10))
                        .distanceType(GeoDistance.PLANE)   // slippy_arc(the default)  arc(most accurate)  plane(fastest)
                        .unit(DistanceUnit.METERS).field("pin.location")
                        .addRange(0, 100).addRange(100, 300).addRange(300, Double.MAX_VALUE))
                .setSize(0);
        printResponse(request);
    }
}
