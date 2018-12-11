package com.qull.service.index.impl;

import com.alibaba.fastjson.JSON;
import com.qull.model.Product;
import com.qull.service.index.IndexService;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.exists.types.TypesExistsResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/6 9:56
 */
@Service
public class IndexServiceImpl<T> implements IndexService<T> {
    private final TransportClient client;

    @Autowired
    public IndexServiceImpl(TransportClient client) {
        this.client = client;
    }

    /**
     * 创建索引
     * @param index
     * @return
     */
    @Override
    public boolean createIndex(String index) {
        if(!isIndexExists(index)) {
            CreateIndexResponse indexResponse = client.admin().indices().create(new CreateIndexRequest().index(index)).actionGet();
//            CreateIndexResponse indexResponse = client.admin().indices().prepareCreate(index).execute().actionGet();
            return indexResponse.isAcknowledged();
        }
        return true;
    }

    @Override
    public boolean importBulk(String index, String type, List<Product> dataList) {
        BulkRequestBuilder bulkRequestBuilder = client.prepareBulk();
        if(!CollectionUtils.isEmpty(dataList)) {
            dataList.forEach(data -> bulkRequestBuilder.add(client.prepareIndex(index, type, data.getFId()).setSource(JSON.toJSONString(data), XContentType.JSON)));
        }
        BulkResponse bulkItemResponses = bulkRequestBuilder.execute().actionGet();
        return bulkItemResponses.hasFailures();
    }

    @Override
    public boolean deleteIndex(String index) {
        if(isIndexExists(index)) {
            DeleteIndexResponse response = client.admin().indices().prepareDelete(index).execute().actionGet();
            return response.isAcknowledged();
        }
        return true;
    }

    /**
     * 判断索引是否存在
     * @param index
     * @return
     */
    @Override
    public boolean isIndexExists(String index) {
        IndicesExistsResponse indicesExistsResponse = client.admin().indices().exists(new IndicesExistsRequest().indices(index)).actionGet();
        return indicesExistsResponse.isExists();
    }

    /**
     * 判断索引下某个type是否存在
     * @param index
     * @param type
     * @return
     */
    @Override
    public boolean isTypeExists(String index, String type) {
        TypesExistsResponse typesExistsResponse = client.admin().indices().prepareTypesExists(index).setTypes(type).execute().actionGet();
        return typesExistsResponse.isExists();
    }
}
