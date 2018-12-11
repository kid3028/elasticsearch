package com.qull.service.search;

import com.qull.constant.ESConstant;
import com.qull.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;


/**
 * @Author kzh
 * @Description
 * @Date 2018/12/6 11:41
 */
@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class SearchServiceTest {
    @Autowired
    private SearchService<Product> searchService;

    @Test
    public void testSearchData() {
        searchService.searchData(ESConstant.ZEDAO_INDEX, ESConstant.ZEDAO_TYPE_PRODUCT);
    }
}
