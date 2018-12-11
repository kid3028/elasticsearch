package com.qull.service.search.impl;

import com.qull.constant.ESProductKey;
import com.qull.model.ESProduct;
import com.qull.service.index.IndexService;
import com.qull.service.search.ProductSearchService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/7 10:36
 */
@Service
@Slf4j
public class ProductSearchServiceImpl implements ProductSearchService {
    private final IndexService<ESProduct> indexService;

    private final TransportClient client;

    @Autowired
    public ProductSearchServiceImpl(IndexService<ESProduct> indexService, TransportClient client) {
        this.indexService = indexService;
        this.client = client;
    }

    @Override
    public SearchHits searchDataByCondition(String index, String type, String keyword, String fields, ESProduct product, String page, String size) {
        if (!indexService.isTypeExists(index, type)) {
            return null;
        }
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        String fCaption = product.getFCaption();
        String fSpell = product.getFSpell();
        String fUseType = product.getFUseType();
        Integer maxSale = product.getMaxSale();
        Integer minSale = product.getMinSale();
        Double maxBuyPrice = product.getMaxBuyPrice();
        Double minBuyPrice = product.getMinBuyPrice();
        String saleSort = product.getSaleSort();
        String priceSort = product.getPriceSort();


        if(Objects.nonNull(maxBuyPrice) && Objects.nonNull(minBuyPrice)) {
            boolQuery.filter(QueryBuilders.rangeQuery(ESProductKey.FBUYPINGRICE).lte(maxBuyPrice).gte(minBuyPrice));
        }else if(Objects.nonNull(maxBuyPrice)) {
            boolQuery.filter(QueryBuilders.rangeQuery(ESProductKey.FBUYPINGRICE).lte(maxBuyPrice));
        }else if(Objects.nonNull(minBuyPrice)) {
            boolQuery.filter(QueryBuilders.rangeQuery(ESProductKey.FBUYPINGRICE).gte(minBuyPrice));
        }

        if(Objects.nonNull(maxSale) && Objects.nonNull(minSale)) {
            boolQuery.filter(QueryBuilders.rangeQuery(ESProductKey.FTOTALSALES).lte(maxSale).gte(minSale));
        }else if(Objects.nonNull(maxSale)) {
            boolQuery.filter(QueryBuilders.rangeQuery(ESProductKey.FTOTALSALES).lte(maxSale));
        }else if(Objects.nonNull(minSale)) {
            boolQuery.filter(QueryBuilders.rangeQuery(ESProductKey.FTOTALSALES).gte(minSale));
        }

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(index).setTypes(type).setQuery(boolQuery);


        if(!StringUtils.isEmpty(keyword)) {
//            MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(keyword,
//                    ESProductKey.FNAME, ESProductKey.FBRAND,
//                    ESProductKey.FDESC,ESProductKey.FSUNMMARY);
//            boolQuery.must(multiMatchQueryBuilder);
            boolQuery.must(QueryBuilders.matchQuery(ESProductKey.FNAME, keyword).minimumShouldMatch("60%"));
        }

        if(!StringUtils.isEmpty(fCaption)) {
            boolQuery.filter(QueryBuilders.termQuery(ESProductKey.FCAPTION, fCaption));
        }
        if(!StringUtils.isEmpty(fSpell)) {
            boolQuery.filter(QueryBuilders.termQuery(ESProductKey.FSPELL, fSpell));
        }
        if(!StringUtils.isEmpty(fUseType)) {
            boolQuery.filter(QueryBuilders.termQuery(ESProductKey.FUSETYPE, fUseType));
        }

        if(!StringUtils.isEmpty(saleSort)) {
            saleSort = saleSort.trim();
            if(ESProductKey.DESC.equals(saleSort)) {
                searchRequestBuilder.addSort(ESProductKey.FTOTALSALES, SortOrder.DESC);
            }else if(ESProductKey.ASC.equals(saleSort)) {
                searchRequestBuilder.addSort(ESProductKey.FTOTALSALES, SortOrder.ASC);
            }
        }

        if(!StringUtils.isEmpty(priceSort)) {
            priceSort = priceSort.trim();
            if(ESProductKey.DESC.equals(priceSort)) {
                searchRequestBuilder.addSort(ESProductKey.FBUYPINGRICE, SortOrder.DESC);
            }else if(ESProductKey.ASC.equals(priceSort)) {
                searchRequestBuilder.addSort(ESProductKey.FBUYPINGRICE, SortOrder.ASC);
            }
        }

        if(!StringUtils.isEmpty(fields)) {
            searchRequestBuilder.setFetchSource(fields.split(","), null);
        }

        if(!StringUtils.isEmpty(page) && !StringUtils.isEmpty(size)) {
            Integer paging = Integer.valueOf(page);
            if(paging <= 0) {
                paging = 0;
            }else {
                --paging;
            }
            Integer sizing = Integer.valueOf(size);
            if(sizing <= 0) {
                sizing = 10;
            }
            searchRequestBuilder.setFrom(paging).setSize(sizing);
        }

        searchRequestBuilder.highlighter(new HighlightBuilder().field(ESProductKey.FNAME).field(ESProductKey.FDESC).preTags("<span style='color:red'>").postTags("</span>"));
        log.info("request : \n {}", searchRequestBuilder);
        SearchResponse response = searchRequestBuilder.execute().actionGet();
        log.info("total : {}, response : \n {}", response.getHits().getTotalHits(), response);
        return response.getHits();
    }
}
