package com.qull.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/6 11:06
 */
@Table(name = "t_product")
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    @GeneratedValue
    private String fId;

    private String fProducerId;
    
    private String fName;

    private String fSpell;
    
    private String fCaption;

    private String fUseType;

    private String fBrand;

    private String fSunmmary;

    private String fDesc;

    private String fState;

    private Double fBuyingPrice;

    private Integer fTotalCount;

    private Integer fTotalSales;

    private LocalDateTime fTime;

}
