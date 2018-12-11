package com.qull.repository;

import com.qull.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/7 14:39
 */
public interface ProductRepository extends JpaRepository<Product, String> {
}
