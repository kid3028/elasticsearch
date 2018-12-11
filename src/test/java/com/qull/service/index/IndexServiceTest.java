package com.qull.service.index;

import com.qull.constant.ESConstant;
import com.qull.model.Product;
import com.qull.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/6 11:09
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class IndexServiceTest {

    @Autowired
    private IndexService<Product> indexService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    public void testCreateIndex() {
        log.info("index 【{}】 is exists? {}", ESConstant.ZEDAO_INDEX, indexService.isIndexExists(ESConstant.ZEDAO_INDEX));
        if (!indexService.isIndexExists(ESConstant.ZEDAO_INDEX)) {
            log.info("index 【{}】 create success? {}", ESConstant.ZEDAO_INDEX, indexService.createIndex(ESConstant.ZEDAO_INDEX));
        }
        log.info("index 【{}】 is exists? {}", ESConstant.ZEDAO_INDEX, indexService.isIndexExists(ESConstant.ZEDAO_INDEX));
    }
    
    @Test
    public void testDeleteIndex() {
        boolean indexExists = indexService.isIndexExists(ESConstant.ZEDAO_INDEX);
        log.info("index 【{}】 is exists? {}", ESConstant.ZEDAO_INDEX, indexExists);
        if(indexExists) {
            indexService.deleteIndex(ESConstant.ZEDAO_INDEX);
        }
        indexExists = indexService.isIndexExists(ESConstant.ZEDAO_INDEX);
        log.info("index 【{}】 is exists? {}", ESConstant.ZEDAO_INDEX, indexExists);
    }


    @Test
    public void testIsIndexExists() {
        boolean indexExists = indexService.isIndexExists(ESConstant.ZEDAO_INDEX);
        log.info("index 【{}】 is exists? {}", ESConstant.ZEDAO_INDEX, indexExists);
        Assert.assertEquals(false, indexExists);
    }

    @Test
    public void testIsTypeExists() {
        boolean typeExists = indexService.isTypeExists(ESConstant.ZEDAO_INDEX, ESConstant.ZEDAO_TYPE_PRODUCT);
        log.info("type 【{}】 of index 【{}】 is exists? {}", ESConstant.ZEDAO_TYPE_PRODUCT, ESConstant.ZEDAO_INDEX, typeExists);
        Assert.assertEquals(true, typeExists);
    }


    @Test
    public void testImportData() {
        List<Product> products = productRepository.findAll();
        boolean result = indexService.importBulk(ESConstant.ZEDAO_INDEX, ESConstant.ZEDAO_TYPE_PRODUCT, products);
        log.info("result is 【{}】", result);
    }


}
