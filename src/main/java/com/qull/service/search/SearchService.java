package com.qull.service.search;

import org.elasticsearch.action.search.SearchResponse;

import java.util.Map;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/6 10:25
 */
public interface SearchService<T> {
    /**
     * 添加文档
     * @param index
     * @param type
     * @param data
     * @return
     */
    String addData(String index, String type, T data);

    /**
     * 更新文档
     * @param index
     * @param type
     * @param id
     * @param data
     * @return
     */
    boolean updateData(String index, String type, String id, T data);

    /**
     * 删除文档
     * @param index
     * @param type
     * @param id
     * @return
     */
    boolean deleteData(String index, String type, String id);

    /**
     * 根据id查询doc
     * @param index
     * @param type
     * @param id
     * @param fields 返回结果中显示字段，使用（,）英文逗号分隔,为null或者空则全部字段返回
     * @return
     */
    Map<String, Object> searchDataById(String index, String type, String id, String  fields);

    /**
     * 如果已经存在对应的doc，则进行更新，否则进行插入
     * @param index
     * @param type
     * @param data
     * @return
     */
    boolean upsetData(String index, String type, T data);

    /**
     * 搜索指定index type下的数据
     * @param index
     * @param type
     * @return
     */
    SearchResponse searchData(String index, String type);
}
