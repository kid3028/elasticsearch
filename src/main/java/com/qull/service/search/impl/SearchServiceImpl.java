package com.qull.service.search.impl;

import com.alibaba.fastjson.JSON;
import com.qull.service.index.IndexService;
import com.qull.service.search.SearchService;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/6 10:40
 */
@Service
public class SearchServiceImpl<T> implements SearchService<T> {
    private final TransportClient client;

    private final IndexService<T> indexService;

    @Autowired
    public SearchServiceImpl(TransportClient client, IndexService<T> indexService) {
        this.client = client;
        this.indexService = indexService;
    }


    @Override
    public String addData(String index, String type, T data) {
        if(!indexService.isIndexExists(index)) {
            indexService.createIndex(index);
        }
        // 新版本的es setSource需要两个
        IndexResponse response = client.prepareIndex(index, type).setSource(JSON.toJSONString(data), XContentType.JSON).execute().actionGet();
        if(RestStatus.CREATED.equals(response.status())) {
            return response.getId();
        }
        return null;
    }

    @Override
    public boolean updateData(String index, String type, String id, T data) {
        if(!indexService.isIndexExists(index)) {
            return false;
        }
        UpdateResponse updateResponse = client.update(new UpdateRequest().index(index).type(type).id(id).doc(JSON.toJSONString(data), XContentType.JSON)).actionGet();
        return RestStatus.OK.equals(updateResponse.status());
    }

    @Override
    public boolean deleteData(String index, String type, String id) {
        DeleteResponse deleteResponse = client.prepareDelete(index, type, id).execute().actionGet();
        return RestStatus.OK.equals(deleteResponse.status());
    }

    @Override
    public Map<String, Object> searchDataById(String index, String type, String id, String fields) {
        if(!indexService.isTypeExists(index, type)) {
            return null;
        }
        GetRequestBuilder getRequestBuilder = client.prepareGet(index, type, id);
        if(!StringUtils.isEmpty(fields)) {
            getRequestBuilder.setFetchSource(fields.split(","), null);
        }
        GetResponse response = getRequestBuilder.execute().actionGet();
        return response.getSource();
    }

    @Override
    public boolean upsetData(String index, String type, T data) {
        IndexRequest indexRequest = new IndexRequest().source(JSON.toJSONString(data), XContentType.JSON);
        UpdateRequest updateRequest = new UpdateRequest().doc(JSON.toJSONString(data), XContentType.JSON).upsert(indexRequest);
        UpdateResponse updateResponse = client.update(updateRequest).actionGet();
        return RestStatus.OK.equals(updateResponse.status());
    }

    @Override
    public SearchResponse searchData(String index, String type) {
        if(indexService.isIndexExists(index)) {
            SearchResponse response = client.prepareSearch(index).setTypes(type).execute().actionGet();
            return response;
        }
        return null;
    }
}
